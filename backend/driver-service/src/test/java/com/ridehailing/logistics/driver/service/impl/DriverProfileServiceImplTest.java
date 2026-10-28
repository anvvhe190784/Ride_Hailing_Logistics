package com.ridehailing.logistics.driver.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import com.ridehailing.logistics.common.domain.enums.VehicleStatus;
import com.ridehailing.logistics.common.domain.enums.VehicleType;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import com.ridehailing.logistics.driver.domain.entity.Vehicle;
import com.ridehailing.logistics.driver.dto.request.DriverKycRequest;
import com.ridehailing.logistics.driver.mapper.DriverMapper;
import com.ridehailing.logistics.driver.repository.DriverProfileRepository;
import com.ridehailing.logistics.driver.repository.VehicleRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
public class DriverProfileServiceImplTest {

  @Mock private DriverProfileRepository driverProfileRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private DriverMapper driverMapper;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private GeoOperations<String, String> geoOperations;

  private ObjectMapper objectMapper;
  private DriverProfileServiceImpl service;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(redisTemplate.opsForGeo()).thenReturn(geoOperations);
    service =
        new DriverProfileServiceImpl(
            driverProfileRepository,
            vehicleRepository,
            driverMapper,
            redisTemplate,
            objectMapper);
  }

  @Test
  void shouldAllowApprovedDriverWithActiveVehicleToBecomeAvailable() {
    UUID driverId = UUID.randomUUID();
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.APPROVED)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    Vehicle activeVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .driverId(driverId)
            .type(VehicleType.BIKE)
            .licensePlate("29A-12345")
            .status(VehicleStatus.ACTIVE)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(vehicleRepository.findByDriverId(driverId)).thenReturn(List.of(activeVehicle));
    when(driverProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(driverMapper.toResponse(any(DriverProfile.class)))
        .thenReturn(
            new DriverProfileResponse(
                driverId,
                "001234567890",
                "123 Hanoi Street",
                DriverReviewStatus.APPROVED,
                null,
                DriverAvailabilityStatus.AVAILABLE));

    DriverProfileResponse response =
        service.updateAvailability(driverId, DriverAvailabilityStatus.AVAILABLE);

    assertNotNull(response);
    assertEquals(DriverAvailabilityStatus.AVAILABLE, response.availabilityStatus());
    assertEquals(DriverAvailabilityStatus.AVAILABLE, profile.getAvailabilityStatus());
    verify(redisTemplate).delete(DriverProfileServiceImpl.REDIS_PROFILE_CACHE_PREFIX + driverId);
    verify(valueOperations).set(DriverProfileServiceImpl.REDIS_DRIVER_AVAILABILITY_PREFIX + driverId, DriverAvailabilityStatus.AVAILABLE.name());
    verify(geoOperations, never()).remove(anyString(), anyString());
    verify(redisTemplate, never()).delete(DriverProfileServiceImpl.REDIS_DRIVER_HEARTBEAT_PREFIX + driverId);
    verify(redisTemplate, never()).delete(DriverProfileServiceImpl.REDIS_DRIVER_LOCATION_PREFIX + driverId);
  }

  @Test
  void shouldCleanUpGeoAndHeartbeatWhenDriverSwitchesToOffline() {
    UUID driverId = UUID.randomUUID();
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.APPROVED)
            .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(driverProfileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(driverMapper.toResponse(any(DriverProfile.class)))
        .thenReturn(
            new DriverProfileResponse(
                driverId,
                "001234567890",
                "123 Hanoi Street",
                DriverReviewStatus.APPROVED,
                null,
                DriverAvailabilityStatus.OFFLINE));

    DriverProfileResponse response =
        service.updateAvailability(driverId, DriverAvailabilityStatus.OFFLINE);

    assertNotNull(response);
    assertEquals(DriverAvailabilityStatus.OFFLINE, response.availabilityStatus());
    verify(redisTemplate).delete(DriverProfileServiceImpl.REDIS_PROFILE_CACHE_PREFIX + driverId);
    verify(valueOperations).set(DriverProfileServiceImpl.REDIS_DRIVER_AVAILABILITY_PREFIX + driverId, DriverAvailabilityStatus.OFFLINE.name());
    verify(geoOperations).remove(DriverProfileServiceImpl.REDIS_GEO_DRIVERS_AVAILABLE, driverId.toString());
    verify(redisTemplate).delete(DriverProfileServiceImpl.REDIS_DRIVER_HEARTBEAT_PREFIX + driverId);
    verify(redisTemplate).delete(DriverProfileServiceImpl.REDIS_DRIVER_LOCATION_PREFIX + driverId);
  }

  @Test
  void shouldRejectAvailabilityUpdateWhenDriverIsNotApproved() {
    UUID driverId = UUID.randomUUID();
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.PENDING_REVIEW)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> service.updateAvailability(driverId, DriverAvailabilityStatus.AVAILABLE));

    assertEquals(422, ex.getHttpStatus());
    assertTrue(ex.getMessage().contains("Only APPROVED drivers"));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void shouldRejectAvailabilityUpdateWhenDriverHasNoVehicles() {
    UUID driverId = UUID.randomUUID();
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.APPROVED)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(vehicleRepository.findByDriverId(driverId)).thenReturn(Collections.emptyList());

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> service.updateAvailability(driverId, DriverAvailabilityStatus.AVAILABLE));

    assertEquals(422, ex.getHttpStatus());
    assertTrue(ex.getMessage().contains("at least one active vehicle"));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void shouldRejectAvailabilityUpdateWhenDriverHasOnlySuspendedVehicle() {
    UUID driverId = UUID.randomUUID();
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.APPROVED)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    Vehicle suspendedVehicle =
        Vehicle.builder()
            .id(UUID.randomUUID())
            .driverId(driverId)
            .type(VehicleType.BIKE)
            .licensePlate("29A-12345")
            .status(VehicleStatus.SUSPENDED)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(vehicleRepository.findByDriverId(driverId)).thenReturn(List.of(suspendedVehicle));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> service.updateAvailability(driverId, DriverAvailabilityStatus.AVAILABLE));

    assertEquals(422, ex.getHttpStatus());
    assertTrue(ex.getMessage().contains("at least one active vehicle"));
    verify(redisTemplate, never()).delete(anyString());
  }

  @Test
  void shouldCacheProfileAndServeSubsequentRequestsFromCache() throws Exception {
    UUID driverId = UUID.randomUUID();
    String cacheKey = DriverProfileServiceImpl.REDIS_PROFILE_CACHE_PREFIX + driverId;
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .userId(driverId)
            .reviewStatus(DriverReviewStatus.APPROVED)
            .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
            .build();

    DriverProfileResponse expectedResponse =
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "123 Hanoi Street",
            DriverReviewStatus.APPROVED,
            null,
            DriverAvailabilityStatus.AVAILABLE);

    String serializedJson = objectMapper.writeValueAsString(expectedResponse);

    // First call: cache miss -> queries DB -> saves to Redis
    // Second call: cache hit -> returns from Redis
    when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(serializedJson);
    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(driverMapper.toResponse(profile)).thenReturn(expectedResponse);

    DriverProfileResponse res1 = service.getProfile(driverId);
    DriverProfileResponse res2 = service.getProfile(driverId);

    assertNotNull(res1);
    assertNotNull(res2);
    assertEquals(expectedResponse, res1);
    assertEquals(expectedResponse, res2);

    verify(driverProfileRepository, times(1)).findById(driverId);
    verify(valueOperations, times(1))
        .set(cacheKey, serializedJson, Duration.ofSeconds(300));
  }

  @Test
  void shouldFallbackToDatabaseWhenRedisCacheIsCorrupted() {
    UUID driverId = UUID.randomUUID();
    String cacheKey = DriverProfileServiceImpl.REDIS_PROFILE_CACHE_PREFIX + driverId;
    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .userId(driverId)
            .reviewStatus(DriverReviewStatus.APPROVED)
            .availabilityStatus(DriverAvailabilityStatus.AVAILABLE)
            .build();

    DriverProfileResponse expectedResponse =
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "123 Hanoi Street",
            DriverReviewStatus.APPROVED,
            null,
            DriverAvailabilityStatus.AVAILABLE);

    when(valueOperations.get(cacheKey)).thenReturn("{ invalid-corrupted-json }");
    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(driverMapper.toResponse(profile)).thenReturn(expectedResponse);

    DriverProfileResponse response = service.getProfile(driverId);

    assertNotNull(response);
    assertEquals(expectedResponse, response);
    verify(driverProfileRepository, times(1)).findById(driverId);
  }

  @Test
  void shouldSubmitKycWithUserIdAndInvalidateCache() {
    UUID driverId = UUID.randomUUID();
    String cacheKey = DriverProfileServiceImpl.REDIS_PROFILE_CACHE_PREFIX + driverId;
    DriverProfile existingProfile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.DRAFT)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(existingProfile));

    var kycReq =
        new DriverKycRequest(
            "001234567890", LocalDate.of(1995, 5, 20), "456 New Street");

    when(driverProfileRepository.save(any(DriverProfile.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    DriverProfileResponse updatedResponse =
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "456 New Street",
            DriverReviewStatus.PENDING_REVIEW,
            null,
            DriverAvailabilityStatus.OFFLINE);
    when(driverMapper.toResponse(any(DriverProfile.class))).thenReturn(updatedResponse);

    DriverProfileResponse kycResponse = service.submitKyc(driverId, kycReq);

    assertEquals(DriverReviewStatus.PENDING_REVIEW, kycResponse.reviewStatus());
    assertEquals(driverId, existingProfile.getUserId());
    verify(redisTemplate).delete(cacheKey);
  }

  @Test
  void shouldInvalidateCacheOnApproveDriver() {
    UUID driverId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    String cacheKey = DriverProfileServiceImpl.REDIS_PROFILE_CACHE_PREFIX + driverId;

    DriverProfile profile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.PENDING_REVIEW)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(driverProfileRepository.save(any(DriverProfile.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    DriverProfileResponse response =
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "123 Hanoi Street",
            DriverReviewStatus.APPROVED,
            "Verified successfully by reviewer",
            DriverAvailabilityStatus.OFFLINE);
    when(driverMapper.toResponse(any(DriverProfile.class))).thenReturn(response);

    DriverProfileResponse result = service.approveDriver(driverId, reviewerId);

    assertNotNull(result);
    assertEquals(DriverReviewStatus.APPROVED, result.reviewStatus());
    verify(redisTemplate).delete(cacheKey);
  }
}
