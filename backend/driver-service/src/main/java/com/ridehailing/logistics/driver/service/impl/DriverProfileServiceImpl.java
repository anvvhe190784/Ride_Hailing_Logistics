package com.ridehailing.logistics.driver.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import com.ridehailing.logistics.common.domain.enums.VehicleStatus;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import com.ridehailing.logistics.driver.domain.entity.Vehicle;
import com.ridehailing.logistics.driver.dto.request.DriverKycRequest;
import com.ridehailing.logistics.driver.dto.request.VehicleCreateRequest;
import com.ridehailing.logistics.driver.dto.response.VehicleResponse;
import com.ridehailing.logistics.driver.mapper.DriverMapper;
import com.ridehailing.logistics.driver.repository.DriverProfileRepository;
import com.ridehailing.logistics.driver.repository.VehicleRepository;
import com.ridehailing.logistics.driver.service.DriverProfileService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverProfileServiceImpl implements DriverProfileService {

  public static final String REDIS_PROFILE_CACHE_PREFIX = "cache:driver:profile:";
  private static final String ERR_DRIVER_NOT_FOUND = "DRIVER_NOT_FOUND";
  private static final String MSG_DRIVER_NOT_FOUND = "Driver profile not found";

  private final DriverProfileRepository driverProfileRepository;
  private final VehicleRepository vehicleRepository;
  private final DriverMapper driverMapper;
  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional
  public DriverProfileResponse submitKyc(UUID driverId, DriverKycRequest request) {
    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElse(DriverProfile.builder().driverId(driverId).userId(driverId).build());

    profile.setUserId(driverId);
    profile.setIdCardNumber(request.idCardNumber());
    profile.setDateOfBirth(request.dateOfBirth());
    profile.setAddress(request.address());
    profile.setReviewStatus(DriverReviewStatus.PENDING_REVIEW);

    profile = driverProfileRepository.save(profile);
    evictDriverProfileCache(driverId);
    log.info("Driver ID {} submitted KYC, status set to PENDING_REVIEW", driverId);
    return driverMapper.toResponse(profile);
  }

  @Override
  @Transactional
  public DriverProfileResponse approveDriver(UUID driverId, UUID reviewerId) {
    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElseThrow(
                () -> BaseDomainException.notFound(ERR_DRIVER_NOT_FOUND, MSG_DRIVER_NOT_FOUND));

    profile.setReviewStatus(DriverReviewStatus.APPROVED);
    profile.setReviewerId(reviewerId);
    profile.setReviewedAt(Instant.now());
    profile.setStatusReason("Verified successfully by reviewer");

    profile = driverProfileRepository.save(profile);
    evictDriverProfileCache(driverId);
    log.info("Driver ID {} approved by Reviewer ID {}", driverId, reviewerId);
    return driverMapper.toResponse(profile);
  }

  @Override
  @Transactional
  public DriverProfileResponse updateAvailability(UUID driverId, DriverAvailabilityStatus status) {
    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElseThrow(
                () -> BaseDomainException.notFound(ERR_DRIVER_NOT_FOUND, MSG_DRIVER_NOT_FOUND));

    // BR-001: Only APPROVED driver with active vehicle can become AVAILABLE
    if (status == DriverAvailabilityStatus.AVAILABLE) {
      if (profile.getReviewStatus() != DriverReviewStatus.APPROVED) {
        throw BaseDomainException.businessRuleViolation(
            "Only APPROVED drivers can switch to AVAILABLE status");
      }
      List<Vehicle> vehicles = vehicleRepository.findByDriverId(driverId);
      boolean hasActiveVehicle =
          vehicles.stream().anyMatch(v -> v.getStatus() == VehicleStatus.ACTIVE);
      if (!hasActiveVehicle) {
        throw BaseDomainException.businessRuleViolation(
            "Driver must have at least one active vehicle before going online");
      }
    }

    profile.setAvailabilityStatus(status);
    profile = driverProfileRepository.save(profile);
    evictDriverProfileCache(driverId);
    log.info("Driver ID {} updated availability to {}", driverId, status);
    return driverMapper.toResponse(profile);
  }

  private void evictDriverProfileCache(UUID driverId) {
    if (redisTemplate != null) {
      try {
        redisTemplate.delete(REDIS_PROFILE_CACHE_PREFIX + driverId);
      } catch (Exception e) {
        log.warn("Failed to evict driver profile from Redis cache for driverId {}: {}", driverId, e.getMessage());
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public DriverProfileResponse getProfile(UUID driverId) {
    String cacheKey = REDIS_PROFILE_CACHE_PREFIX + driverId;
    if (redisTemplate != null) {
      try {
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
          return objectMapper.readValue(cachedJson, DriverProfileResponse.class);
        }
      } catch (Exception e) {
        log.warn("Failed to read driver profile from Redis cache for driverId {}: {}", driverId, e.getMessage());
      }
    }

    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElseThrow(
                () -> BaseDomainException.notFound(ERR_DRIVER_NOT_FOUND, MSG_DRIVER_NOT_FOUND));
    DriverProfileResponse response = driverMapper.toResponse(profile);

    if (redisTemplate != null) {
      try {
        redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofSeconds(300));
      } catch (Exception e) {
        log.warn("Failed to write driver profile to Redis cache for driverId {}: {}", driverId, e.getMessage());
      }
    }

    return response;
  }

  @Override
  @Transactional
  public VehicleResponse registerVehicle(UUID driverId, VehicleCreateRequest request) {
    if (vehicleRepository.existsByLicensePlate(request.licensePlate())) {
      throw BaseDomainException.conflict(
          "PLATE_ALREADY_EXISTS", "Vehicle license plate already registered");
    }

    Vehicle vehicle =
        Vehicle.builder()
            .driverId(driverId)
            .type(request.type())
            .licensePlate(request.licensePlate())
            .brand(request.brand())
            .model(request.model())
            .color(request.color())
            .build();

    vehicle = vehicleRepository.save(vehicle);
    log.info("Registered vehicle ID {} for driver ID {}", vehicle.getId(), driverId);
    return driverMapper.toResponse(vehicle);
  }

  @Override
  @Transactional(readOnly = true)
  public List<VehicleResponse> getVehicles(UUID driverId) {
    return vehicleRepository.findByDriverId(driverId).stream()
        .map(driverMapper::toResponse)
        .toList();
  }
}
