package com.ridehailing.logistics.driver.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import com.ridehailing.logistics.common.domain.enums.VehicleStatus;
import com.ridehailing.logistics.common.domain.enums.VehicleType;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import com.ridehailing.logistics.driver.domain.entity.Vehicle;
import com.ridehailing.logistics.driver.mapper.DriverMapper;
import com.ridehailing.logistics.driver.repository.DriverProfileRepository;
import com.ridehailing.logistics.driver.repository.VehicleRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverProfileServiceImplTest {

  @Mock private DriverProfileRepository driverProfileRepository;
  @Mock private VehicleRepository vehicleRepository;
  @Mock private DriverMapper driverMapper;

  private DriverProfileServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new DriverProfileServiceImpl(driverProfileRepository, vehicleRepository, driverMapper);
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
    when(driverMapper.toResponse(any(DriverProfile.class))).thenReturn(
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "123 Hanoi Street",
            DriverReviewStatus.APPROVED,
            null,
            DriverAvailabilityStatus.AVAILABLE));

    DriverProfileResponse response = service.updateAvailability(driverId, DriverAvailabilityStatus.AVAILABLE);

    assertNotNull(response);
    assertEquals(DriverAvailabilityStatus.AVAILABLE, response.availabilityStatus());
    assertEquals(DriverAvailabilityStatus.AVAILABLE, profile.getAvailabilityStatus());
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
  }

  @Test
  void shouldCacheProfileAndServeSubsequentRequestsFromCache() {
    UUID driverId = UUID.randomUUID();
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

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(profile));
    when(driverMapper.toResponse(profile)).thenReturn(expectedResponse);

    DriverProfileResponse res1 = service.getProfile(driverId);
    DriverProfileResponse res2 = service.getProfile(driverId);

    assertNotNull(res1);
    assertSame(res1, res2);
    verify(driverProfileRepository, times(1)).findById(driverId);
  }

  @Test
  void shouldSubmitKycWithUserIdAndInvalidateCache() {
    UUID driverId = UUID.randomUUID();
    DriverProfile existingProfile =
        DriverProfile.builder()
            .driverId(driverId)
            .reviewStatus(DriverReviewStatus.DRAFT)
            .availabilityStatus(DriverAvailabilityStatus.OFFLINE)
            .build();

    DriverProfileResponse initialResponse =
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "123 Hanoi Street",
            DriverReviewStatus.DRAFT,
            null,
            DriverAvailabilityStatus.OFFLINE);

    when(driverProfileRepository.findById(driverId)).thenReturn(Optional.of(existingProfile));
    when(driverMapper.toResponse(existingProfile)).thenReturn(initialResponse);

    // Warm cache
    service.getProfile(driverId);
    verify(driverProfileRepository, times(1)).findById(driverId);

    var kycReq =
        new com.ridehailing.logistics.driver.dto.request.DriverKycRequest(
            "001234567890", java.time.LocalDate.of(1995, 5, 20), "456 New Street");

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

    // Subsequent getProfile should query repository again because cache was evicted (1st getProfile + 1st submitKyc + 2nd getProfile = 3)
    service.getProfile(driverId);
    verify(driverProfileRepository, times(3)).findById(driverId);
  }
}
