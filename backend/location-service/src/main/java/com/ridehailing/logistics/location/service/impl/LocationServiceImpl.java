package com.ridehailing.logistics.location.service.impl;

import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.location.domain.entity.DriverLatestLocation;
import com.ridehailing.logistics.location.domain.entity.DriverTelemetryHistory;
import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import com.ridehailing.logistics.location.repository.DriverLatestLocationRepository;
import com.ridehailing.logistics.location.repository.DriverTelemetryHistoryRepository;
import com.ridehailing.logistics.location.service.LocationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoRadiusCommandArgs;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class LocationServiceImpl implements LocationService {

  public static final String REDIS_GEO_DRIVERS_AVAILABLE = "geo:drivers:available";
  public static final String REDIS_DRIVER_AVAILABILITY_PREFIX = "driver:availability:";
  public static final String REDIS_DRIVER_HEARTBEAT_PREFIX = "driver:heartbeat:";
  private static final String REDIS_LOCATION_KEY_PREFIX = "driver:location:";
  private final DriverLatestLocationRepository locationRepository;
  private final StringRedisTemplate redisTemplate;
  private final DriverTelemetryHistoryRepository telemetryHistoryRepository;
  private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

  public LocationServiceImpl(
      DriverLatestLocationRepository locationRepository,
      StringRedisTemplate redisTemplate) {
    this(locationRepository, redisTemplate, null);
  }

  @Autowired
  public LocationServiceImpl(
      DriverLatestLocationRepository locationRepository,
      StringRedisTemplate redisTemplate,
      @Autowired(required = false) DriverTelemetryHistoryRepository telemetryHistoryRepository) {
    this.locationRepository = locationRepository;
    this.redisTemplate = redisTemplate;
    this.telemetryHistoryRepository = telemetryHistoryRepository;
  }

  @Override
  public void processTelemetry(UUID driverId, TelemetryRequest request) {
    if (request.speedMps() != null && (request.speedMps() < 0.0 || request.speedMps() > 69.44)) {
      throw BaseDomainException.badRequest(
          "GPS_TELEPORTATION_DETECTED",
          "Invalid telemetry speed: exceeds maximum physical threshold of 250 km/h (69.44 m/s)");
    }

    Optional<DriverLatestLocation> existingOpt = locationRepository.findById(driverId);

    // FR-LOC-006: Reject out-of-order or duplicate telemetry sequences
    if (existingOpt.isPresent()
        && existingOpt.get().getSequenceNum() != null
        && request.sequenceNum() <= existingOpt.get().getSequenceNum()) {
      log.debug(
          "Ignoring out-of-order telemetry for driver {}: incoming seq {}, existing seq {}",
          driverId,
          request.sequenceNum(),
          existingOpt.get().getSequenceNum());
      return;
    }

    Point point =
        geometryFactory.createPoint(
            new Coordinate(request.longitude().doubleValue(), request.latitude().doubleValue()));

    DriverLatestLocation latestLocation =
        existingOpt.orElse(DriverLatestLocation.builder().driverId(driverId).build());
    latestLocation.setLocation(point);
    latestLocation.setLatitude(request.latitude());
    latestLocation.setLongitude(request.longitude());
    latestLocation.setAccuracyMeters(request.accuracyMeters());
    latestLocation.setHeadingDegrees(request.headingDegrees());
    latestLocation.setSpeedMps(request.speedMps());
    latestLocation.setSequenceNum(request.sequenceNum());
    latestLocation.setDeviceTimestamp(request.deviceTimestamp());

    String availStr = null;
    if (redisTemplate != null) {
      try {
        availStr = redisTemplate.opsForValue().get(REDIS_DRIVER_AVAILABILITY_PREFIX + driverId);
      } catch (Exception ex) {
        log.warn(
            "Failed to read driver availability from Redis for driver {}: {}",
            driverId,
            ex.getMessage());
      }
    }
    boolean isAvailable =
        availStr != null
            ? "AVAILABLE".equalsIgnoreCase(availStr)
            : existingOpt.map(DriverLatestLocation::isAvailable).orElse(false);
    latestLocation.setAvailable(isAvailable);

    locationRepository.save(latestLocation);

    // Persist telemetry history if repository is present
    if (telemetryHistoryRepository != null) {
      try {
        DriverTelemetryHistory history =
            DriverTelemetryHistory.builder()
                .driverId(driverId)
                .location(point)
                .latitude(request.latitude().doubleValue())
                .longitude(request.longitude().doubleValue())
                .speed(request.speedMps())
                .bearing(request.headingDegrees())
                .accuracy(request.accuracyMeters())
                .recordedAt(
                    request.deviceTimestamp() != null ? request.deviceTimestamp() : Instant.now())
                .build();
        telemetryHistoryRepository.save(history);
      } catch (Exception ex) {
        log.warn(
            "Failed to persist telemetry history for driver {}: {}", driverId, ex.getMessage());
      }
    }

    // Redis operations performed completely outside DB transaction boundary
    try {
      if (isAvailable) {
        // Redis GEO for radius searches
        redisTemplate
            .opsForGeo()
            .add(
                REDIS_GEO_DRIVERS_AVAILABLE,
                new org.springframework.data.geo.Point(
                    request.longitude().doubleValue(), request.latitude().doubleValue()),
                driverId.toString());
        redisTemplate
            .opsForValue()
            .set(
                REDIS_DRIVER_HEARTBEAT_PREFIX + driverId,
                String.valueOf(System.currentTimeMillis()),
                Duration.ofSeconds(60));
      } else {
        redisTemplate.opsForGeo().remove(REDIS_GEO_DRIVERS_AVAILABLE, driverId.toString());
        redisTemplate.delete(REDIS_DRIVER_HEARTBEAT_PREFIX + driverId);
      }

      // Session 11: Write to Redis cache with TTL 60 seconds (DR-GEO-003)
      String redisKey = REDIS_LOCATION_KEY_PREFIX + driverId;
      String val = request.latitude() + ":" + request.longitude() + ":" + request.sequenceNum();
      redisTemplate.opsForValue().set(redisKey, val, Duration.ofSeconds(60));
    } catch (Exception ex) {
      log.warn("Failed to update telemetry in Redis for driver {}: {}", driverId, ex.getMessage());
    }

    log.debug(
        "Processed telemetry for driver {}: lat {}, lng {}",
        driverId,
        request.latitude(),
        request.longitude());
  }

  @Override
  public List<NearbyDriverResponse> findNearbyDrivers(
      double latitude, double longitude, double radiusMeters, int limit) {
    int safeLimit = Math.clamp(limit, 1, 100);

    // Step 1: Attempt to query Redis GEO for available drivers within radius
    if (redisTemplate != null) {
      try {
        Circle circle =
            new Circle(
                new org.springframework.data.geo.Point(longitude, latitude),
                new Distance(radiusMeters, RedisGeoCommands.DistanceUnit.METERS));
        GeoRadiusCommandArgs args =
            GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()
                .includeCoordinates()
                .sortAscending()
                .limit(safeLimit);

        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults =
            redisTemplate.opsForGeo().radius(REDIS_GEO_DRIVERS_AVAILABLE, circle, args);

        if (geoResults != null && !geoResults.getContent().isEmpty()) {
          List<NearbyDriverResponse> nearbyDrivers = new ArrayList<>();
          for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : geoResults) {
            NearbyDriverResponse driver = mapGeoResultToNearbyDriver(result, latitude, longitude);
            if (driver != null) {
              nearbyDrivers.add(driver);
            }
          }
          if (!nearbyDrivers.isEmpty()) {
            log.debug("Found {} available drivers via Redis GEO cache", nearbyDrivers.size());
            return nearbyDrivers;
          }
        }
      } catch (Exception ex) {
        log.warn(
            "Failed to query nearby drivers from Redis GEO, falling back to database: {}",
            ex.getMessage());
      }
    }

    // Step 2: Fallback to database query if Redis returns empty, null, or threw exception
    Instant cutoffTime = Instant.now().minus(Duration.ofSeconds(120));
    return locationRepository
        .findAvailableDriversNearby(latitude, longitude, radiusMeters, cutoffTime, safeLimit)
        .stream()
        .map(
            p ->
                new NearbyDriverResponse(
                    p.getDriverId(), p.getLatitude(), p.getLongitude(), p.getDistanceMeters()))
        .toList();
  }

  private NearbyDriverResponse mapGeoResultToNearbyDriver(
      GeoResult<RedisGeoCommands.GeoLocation<String>> result, double defaultLat, double defaultLng) {
    if (result == null || result.getContent() == null || result.getContent().getName() == null) {
      return null;
    }
    String driverIdStr = result.getContent().getName();
    String heartbeatKey = REDIS_DRIVER_HEARTBEAT_PREFIX + driverIdStr;
    if (!Boolean.TRUE.equals(redisTemplate.hasKey(heartbeatKey))) {
      return null;
    }
    try {
      UUID driverUuid = UUID.fromString(driverIdStr);
      org.springframework.data.geo.Point pt = result.getContent().getPoint();
      double driverLng = pt != null ? pt.getX() : defaultLng;
      double driverLat = pt != null ? pt.getY() : defaultLat;
      double distance = result.getDistance() != null ? result.getDistance().getValue() : 0.0;
      return new NearbyDriverResponse(driverUuid, driverLat, driverLng, distance);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
