package com.ridehailing.logistics.driver.service.impl;

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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverProfileServiceImpl implements DriverProfileService {

  private static final String ERR_DRIVER_NOT_FOUND = "DRIVER_NOT_FOUND";
  private static final String MSG_DRIVER_NOT_FOUND = "Driver profile not found";
  private static final long PROFILE_CACHE_TTL_MS = 60_000L;

  private record CachedProfile(DriverProfileResponse response, long expireAtMillis) {
    boolean isExpired() {
      return System.currentTimeMillis() > expireAtMillis;
    }
  }

  private final Map<UUID, CachedProfile> profileCache = new ConcurrentHashMap<>();

  private final DriverProfileRepository driverProfileRepository;
  private final VehicleRepository vehicleRepository;
  private final DriverMapper driverMapper;

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
    profileCache.remove(driverId);
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
    profileCache.remove(driverId);
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
    profileCache.remove(driverId);
    log.info("Driver ID {} updated availability to {}", driverId, status);
    return driverMapper.toResponse(profile);
  }

  @Override
  @Transactional(readOnly = true)
  public DriverProfileResponse getProfile(UUID driverId) {
    CachedProfile cached = profileCache.get(driverId);
    if (cached != null && !cached.isExpired()) {
      return cached.response();
    }
    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElseThrow(
                () -> BaseDomainException.notFound(ERR_DRIVER_NOT_FOUND, MSG_DRIVER_NOT_FOUND));
    DriverProfileResponse response = driverMapper.toResponse(profile);
    profileCache.put(
        driverId, new CachedProfile(response, System.currentTimeMillis() + PROFILE_CACHE_TTL_MS));
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
