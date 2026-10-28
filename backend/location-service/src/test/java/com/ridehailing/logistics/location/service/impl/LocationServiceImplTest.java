package com.ridehailing.logistics.location.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.location.domain.entity.DriverLatestLocation;
import com.ridehailing.logistics.location.domain.entity.DriverTelemetryHistory;
import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import com.ridehailing.logistics.location.repository.DriverLatestLocationRepository;
import com.ridehailing.logistics.location.repository.DriverLatestLocationRepository.NearbyDriverProjection;
import com.ridehailing.logistics.location.repository.DriverTelemetryHistoryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LocationServiceImplTest {

  @Mock private DriverLatestLocationRepository locationRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private DriverTelemetryHistoryRepository telemetryHistoryRepository;
  @Mock private GeoOperations<String, String> geoOperations;
  @Mock private ValueOperations<String, String> valueOperations;

  private LocationServiceImpl locationService;

  @BeforeEach
  void setUp() {
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    locationService =
        new LocationServiceImpl(locationRepository, redisTemplate, telemetryHistoryRepository);
  }

  @Test
  void shouldProcessTelemetryAndPersistLocationAndHistory() {
    UUID driverId = UUID.randomUUID();
    TelemetryRequest request =
        new TelemetryRequest(
            new BigDecimal("21.0285110"),
            new BigDecimal("105.8544441"),
            5.0,
            90.0,
            12.5,
            100L,
            Instant.now());

    when(locationRepository.findById(driverId)).thenReturn(Optional.empty());
    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(LocationServiceImpl.REDIS_DRIVER_AVAILABILITY_PREFIX + driverId))
        .thenReturn("AVAILABLE");

    locationService.processTelemetry(driverId, request);

    ArgumentCaptor<DriverLatestLocation> locCaptor =
        ArgumentCaptor.forClass(DriverLatestLocation.class);
    verify(locationRepository, times(1)).save(locCaptor.capture());
    assertTrue(locCaptor.getValue().isAvailable());
    verify(telemetryHistoryRepository, times(1)).save(any(DriverTelemetryHistory.class));
    verify(geoOperations, times(1))
        .add(eq("geo:drivers:available"), any(org.springframework.data.geo.Point.class), eq(driverId.toString()));
    verify(valueOperations, times(1))
        .set(eq("driver:heartbeat:" + driverId), anyString(), any());
  }

  @Test
  void shouldProcessTelemetryWhenDriverIsOfflineOrUnavailable() {
    UUID driverId = UUID.randomUUID();
    TelemetryRequest request =
        new TelemetryRequest(
            new BigDecimal("21.0285110"),
            new BigDecimal("105.8544441"),
            5.0,
            90.0,
            12.5,
            100L,
            Instant.now());

    when(locationRepository.findById(driverId)).thenReturn(Optional.empty());
    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(LocationServiceImpl.REDIS_DRIVER_AVAILABILITY_PREFIX + driverId))
        .thenReturn("OFFLINE");

    locationService.processTelemetry(driverId, request);

    ArgumentCaptor<DriverLatestLocation> locCaptor =
        ArgumentCaptor.forClass(DriverLatestLocation.class);
    verify(locationRepository, times(1)).save(locCaptor.capture());
    assertFalse(locCaptor.getValue().isAvailable());

    verify(geoOperations, times(1)).remove(eq("geo:drivers:available"), eq(driverId.toString()));
    verify(geoOperations, never())
        .add(anyString(), any(org.springframework.data.geo.Point.class), anyString());
    verify(redisTemplate, times(1)).delete(eq("driver:heartbeat:" + driverId));
  }

  @Test
  void shouldIgnoreOutOfOrderTelemetry() {
    UUID driverId = UUID.randomUUID();
    DriverLatestLocation existing =
        DriverLatestLocation.builder()
            .driverId(driverId)
            .sequenceNum(200L)
            .build();

    TelemetryRequest request =
        new TelemetryRequest(
            new BigDecimal("21.0285110"),
            new BigDecimal("105.8544441"),
            5.0,
            90.0,
            12.5,
            150L, // Lower sequence number
            Instant.now());

    when(locationRepository.findById(driverId)).thenReturn(Optional.of(existing));

    locationService.processTelemetry(driverId, request);

    verify(locationRepository, never()).save(any());
    verify(telemetryHistoryRepository, never()).save(any());
    verifyNoInteractions(redisTemplate);
  }

  @Test
  void shouldReturnNearbyDriversFromRedisWhenHeartbeatIsValid() {
    UUID driverId = UUID.randomUUID();
    double lat = 21.028511;
    double lng = 105.854444;

    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(redisTemplate.hasKey("driver:heartbeat:" + driverId)).thenReturn(true);

    RedisGeoCommands.GeoLocation<String> geoLocation =
        new RedisGeoCommands.GeoLocation<>(
            driverId.toString(), new org.springframework.data.geo.Point(lng, lat));
    GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult =
        new GeoResult<>(geoLocation, new Distance(350.0, RedisGeoCommands.DistanceUnit.METERS));
    GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults =
        new GeoResults<>(List.of(geoResult));

    when(geoOperations.radius(eq("geo:drivers:available"), any(Circle.class), any(GeoRadiusCommandArgs.class)))
        .thenReturn(geoResults);

    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(lat, lng, 3000.0, 10);

    assertNotNull(drivers);
    assertEquals(1, drivers.size());
    assertEquals(driverId, drivers.get(0).driverId());
    assertEquals(350.0, drivers.get(0).distanceMeters());

    // DB should NOT be called when Redis has valid drivers
    verify(locationRepository, never())
        .findAvailableDriversNearby(anyDouble(), anyDouble(), anyDouble(), any(), anyInt());
  }

  @Test
  void shouldFallbackToDatabaseWhenRedisDriverHeartbeatIsExpired() {
    UUID driverId = UUID.randomUUID();
    double lat = 21.028511;
    double lng = 105.854444;

    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(redisTemplate.hasKey("driver:heartbeat:" + driverId)).thenReturn(false);

    RedisGeoCommands.GeoLocation<String> geoLocation =
        new RedisGeoCommands.GeoLocation<>(
            driverId.toString(), new org.springframework.data.geo.Point(lng, lat));
    GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult =
        new GeoResult<>(geoLocation, new Distance(350.0, RedisGeoCommands.DistanceUnit.METERS));
    GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults =
        new GeoResults<>(List.of(geoResult));

    when(geoOperations.radius(eq("geo:drivers:available"), any(Circle.class), any(GeoRadiusCommandArgs.class)))
        .thenReturn(geoResults);

    NearbyDriverProjection proj = mock(NearbyDriverProjection.class);
    UUID dbDriverId = UUID.randomUUID();
    when(proj.getDriverId()).thenReturn(dbDriverId);
    when(proj.getLatitude()).thenReturn(lat);
    when(proj.getLongitude()).thenReturn(lng);
    when(proj.getDistanceMeters()).thenReturn(500.0);

    when(locationRepository.findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10)))
        .thenReturn(List.of(proj));

    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(lat, lng, 3000.0, 10);

    assertNotNull(drivers);
    assertEquals(1, drivers.size());
    assertEquals(dbDriverId, drivers.get(0).driverId());
    verify(locationRepository, times(1))
        .findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10));
  }

  @Test
  void shouldFallbackToDatabaseWhenRedisThrowsException() {
    double lat = 21.028511;
    double lng = 105.854444;

    when(redisTemplate.opsForGeo()).thenThrow(new RuntimeException("Redis connection refused"));

    NearbyDriverProjection proj = mock(NearbyDriverProjection.class);
    UUID dbDriverId = UUID.randomUUID();
    when(proj.getDriverId()).thenReturn(dbDriverId);
    when(proj.getLatitude()).thenReturn(lat);
    when(proj.getLongitude()).thenReturn(lng);
    when(proj.getDistanceMeters()).thenReturn(600.0);

    when(locationRepository.findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10)))
        .thenReturn(List.of(proj));

    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(lat, lng, 3000.0, 10);

    assertNotNull(drivers);
    assertEquals(1, drivers.size());
    assertEquals(dbDriverId, drivers.get(0).driverId());
    verify(locationRepository, times(1))
        .findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10));
  }

  @Test
  void shouldFallbackToDatabaseWhenRedisReturnsEmptyResults() {
    double lat = 21.028511;
    double lng = 105.854444;

    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(geoOperations.radius(eq("geo:drivers:available"), any(Circle.class), any(GeoRadiusCommandArgs.class)))
        .thenReturn(new GeoResults<>(Collections.emptyList()));

    when(locationRepository.findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10)))
        .thenReturn(Collections.emptyList());

    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(lat, lng, 3000.0, 10);

    assertNotNull(drivers);
    assertTrue(drivers.isEmpty());
    verify(locationRepository, times(1))
        .findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10));
  }

  @Test
  void shouldFilterOutOfflineDriversFromDatabaseFallbackWhenRedisMarksThemOffline() {
    double lat = 21.028511;
    double lng = 105.854444;

    when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    when(geoOperations.radius(eq("geo:drivers:available"), any(Circle.class), any(GeoRadiusCommandArgs.class)))
        .thenReturn(new GeoResults<>(Collections.emptyList()));

    UUID offlineDriverId = UUID.randomUUID();
    UUID availableDriverId = UUID.randomUUID();

    NearbyDriverProjection offlineProj = mock(NearbyDriverProjection.class);
    when(offlineProj.getDriverId()).thenReturn(offlineDriverId);
    when(offlineProj.getLatitude()).thenReturn(lat);
    when(offlineProj.getLongitude()).thenReturn(lng);
    when(offlineProj.getDistanceMeters()).thenReturn(100.0);

    NearbyDriverProjection availableProj = mock(NearbyDriverProjection.class);
    when(availableProj.getDriverId()).thenReturn(availableDriverId);
    when(availableProj.getLatitude()).thenReturn(lat + 0.001);
    when(availableProj.getLongitude()).thenReturn(lng + 0.001);
    when(availableProj.getDistanceMeters()).thenReturn(200.0);

    when(locationRepository.findAvailableDriversNearby(eq(lat), eq(lng), eq(3000.0), any(), eq(10)))
        .thenReturn(List.of(offlineProj, availableProj));

    when(valueOperations.get("driver:availability:" + offlineDriverId)).thenReturn("OFFLINE");
    when(valueOperations.get("driver:availability:" + availableDriverId)).thenReturn("AVAILABLE");

    DriverLatestLocation offlineLoc =
        DriverLatestLocation.builder().driverId(offlineDriverId).isAvailable(true).build();
    when(locationRepository.findById(offlineDriverId)).thenReturn(Optional.of(offlineLoc));

    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(lat, lng, 3000.0, 10);

    assertNotNull(drivers);
    assertEquals(1, drivers.size());
    assertEquals(availableDriverId, drivers.get(0).driverId());
    assertFalse(offlineLoc.isAvailable());
    verify(locationRepository, times(1)).save(offlineLoc);
  }
}
