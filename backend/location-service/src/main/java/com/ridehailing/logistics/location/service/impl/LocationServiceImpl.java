package com.ridehailing.logistics.location.service.impl;

import com.ridehailing.logistics.location.domain.entity.DriverLatestLocation;
import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import com.ridehailing.logistics.location.repository.DriverLatestLocationRepository;
import com.ridehailing.logistics.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private final DriverLatestLocationRepository locationRepository;
    private final StringRedisTemplate redisTemplate;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    private static final String REDIS_LOCATION_KEY_PREFIX = "driver:location:";

    @Override
    @Transactional
    public void processTelemetry(UUID driverId, TelemetryRequest request) {
        Optional<DriverLatestLocation> existingOpt = locationRepository.findById(driverId);

        // FR-LOC-006: Reject out-of-order or duplicate telemetry sequences
        if (existingOpt.isPresent() && request.sequenceNum() <= existingOpt.get().getSequenceNum()) {
            log.debug("Ignoring out-of-order telemetry for driver {}: incoming seq {}, existing seq {}",
                    driverId, request.sequenceNum(), existingOpt.get().getSequenceNum());
            return;
        }

        Point point = geometryFactory.createPoint(new Coordinate(
                request.longitude().doubleValue(),
                request.latitude().doubleValue()
        ));

        DriverLatestLocation record = existingOpt.orElse(DriverLatestLocation.builder().driverId(driverId).build());
        record.setLocation(point);
        record.setLatitude(request.latitude());
        record.setLongitude(request.longitude());
        record.setAccuracyMeters(request.accuracyMeters());
        record.setHeadingDegrees(request.headingDegrees());
        record.setSpeedMps(request.speedMps());
        record.setSequenceNum(request.sequenceNum());
        record.setDeviceTimestamp(request.deviceTimestamp());

        locationRepository.save(record);

        // Session 11: Write to Redis cache with TTL 60 seconds (DR-GEO-003)
        String redisKey = REDIS_LOCATION_KEY_PREFIX + driverId;
        String val = request.latitude() + ":" + request.longitude() + ":" + request.sequenceNum();
        redisTemplate.opsForValue().set(redisKey, val, Duration.ofSeconds(60));

        log.debug("Processed telemetry for driver {}: lat {}, lng {}", driverId, request.latitude(), request.longitude());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyDriverResponse> findNearbyDrivers(double latitude, double longitude, double radiusMeters, int limit) {
        return locationRepository.findAvailableDriversNearby(latitude, longitude, radiusMeters, limit).stream()
                .map(p -> new NearbyDriverResponse(p.getDriverId(), p.getLatitude(), p.getLongitude(), p.getDistanceMeters()))
                .toList();
    }
}
