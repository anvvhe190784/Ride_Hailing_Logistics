package com.ridehailing.logistics.driver.service.impl;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import com.ridehailing.logistics.driver.domain.entity.Vehicle;
import com.ridehailing.logistics.driver.dto.request.DriverKycRequest;
import com.ridehailing.logistics.driver.dto.request.VehicleCreateRequest;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.driver.dto.response.VehicleResponse;
import com.ridehailing.logistics.driver.mapper.DriverMapper;
import com.ridehailing.logistics.driver.repository.DriverProfileRepository;
import com.ridehailing.logistics.driver.repository.VehicleRepository;
import com.ridehailing.logistics.driver.service.DriverProfileService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DriverProfileServiceImpl implements DriverProfileService {

  private final DriverProfileRepository driverProfileRepository;
  private final VehicleRepository vehicleRepository;
  private final DriverMapper driverMapper;

  @Override
  @Transactional
  public DriverProfileResponse submitKyc(UUID driverId, DriverKycRequest request) {
    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElse(DriverProfile.builder().driverId(driverId).build());

    profile.setIdCardNumber(request.idCardNumber());
    profile.setDateOfBirth(request.dateOfBirth());
    profile.setAddress(request.address());
    profile.setReviewStatus(DriverReviewStatus.PENDING_REVIEW);

    profile = driverProfileRepository.save(profile);
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
                () -> BaseDomainException.notFound("DRIVER_NOT_FOUND", "Driver profile not found"));

    profile.setReviewStatus(DriverReviewStatus.APPROVED);
    profile.setReviewerId(reviewerId);
    profile.setReviewedAt(Instant.now());
    profile.setStatusReason("Verified successfully by reviewer");

    profile = driverProfileRepository.save(profile);
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
                () -> BaseDomainException.notFound("DRIVER_NOT_FOUND", "Driver profile not found"));

    // BR-001: Only APPROVED driver with active vehicle can become AVAILABLE
    if (status == DriverAvailabilityStatus.AVAILABLE) {
      if (profile.getReviewStatus() != DriverReviewStatus.APPROVED) {
        throw BaseDomainException.businessRuleViolation(
            "Only APPROVED drivers can switch to AVAILABLE status");
      }
      List<Vehicle> vehicles = vehicleRepository.findByDriverId(driverId);
      if (vehicles.isEmpty()) {
        throw BaseDomainException.businessRuleViolation(
            "Driver must register at least one vehicle before going online");
      }
    }

    profile.setAvailabilityStatus(status);
    profile = driverProfileRepository.save(profile);
    log.info("Driver ID {} updated availability to {}", driverId, status);
    return driverMapper.toResponse(profile);
  }

  @Override
  @Transactional(readOnly = true)
  public DriverProfileResponse getProfile(UUID driverId) {
    DriverProfile profile =
        driverProfileRepository
            .findById(driverId)
            .orElseThrow(
                () -> BaseDomainException.notFound("DRIVER_NOT_FOUND", "Driver profile not found"));
    return driverMapper.toResponse(profile);
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
