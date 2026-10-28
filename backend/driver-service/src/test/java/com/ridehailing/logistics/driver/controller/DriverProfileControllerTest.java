package com.ridehailing.logistics.driver.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.driver.service.DriverProfileService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DriverProfileControllerTest {

  @Mock private DriverProfileService driverProfileService;

  private DriverProfileController controller;

  @BeforeEach
  void setUp() {
    controller = new DriverProfileController(driverProfileService);
  }

  @Test
  void shouldAllowAdminOrReviewerToApproveDriver() {
    UUID driverId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();
    DriverProfileResponse profileResponse =
        new DriverProfileResponse(
            driverId,
            "001234567890",
            "123 Hanoi Street",
            DriverReviewStatus.APPROVED,
            "Verified",
            DriverAvailabilityStatus.OFFLINE);

    when(driverProfileService.approveDriver(driverId, reviewerId)).thenReturn(profileResponse);

    ResponseEntity<ApiResponse<DriverProfileResponse>> response =
        controller.approveDriver(driverId, reviewerId, "ADMIN", "corr-1");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(DriverReviewStatus.APPROVED, response.getBody().getData().reviewStatus());
  }

  @Test
  void shouldRejectApprovalWhenCallerIsNotAdminOrReviewer() {
    UUID driverId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> controller.approveDriver(driverId, reviewerId, "CUSTOMER", "corr-1"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("ACCESS_DENIED", ex.getErrorCode());
    verify(driverProfileService, never()).approveDriver(any(), any());
  }

  @Test
  void shouldRejectApprovalWhenCallerRoleHeaderIsMissing() {
    UUID driverId = UUID.randomUUID();
    UUID reviewerId = UUID.randomUUID();

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> controller.approveDriver(driverId, reviewerId, null, "corr-1"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("ACCESS_DENIED", ex.getErrorCode());
    verify(driverProfileService, never()).approveDriver(any(), any());
  }

  @Test
  void shouldAllowDriverOrAdminToSubmitKyc() {
    UUID driverId = UUID.randomUUID();
    var kycReq =
        new com.ridehailing.logistics.driver.dto.request.DriverKycRequest(
            "012345678901", java.time.LocalDate.of(1990, 1, 1), "123 Street");
    var profileResponse =
        new DriverProfileResponse(
            driverId,
            "012345678901",
            "123 Street",
            DriverReviewStatus.PENDING_REVIEW,
            null,
            DriverAvailabilityStatus.OFFLINE);
    when(driverProfileService.submitKyc(driverId, kycReq)).thenReturn(profileResponse);

    ResponseEntity<ApiResponse<DriverProfileResponse>> res =
        controller.submitKyc(driverId, "DRIVER", kycReq, "corr-1");
    assertEquals(200, res.getStatusCode().value());

    ResponseEntity<ApiResponse<DriverProfileResponse>> adminRes =
        controller.submitKyc(driverId, "ADMIN", kycReq, "corr-2");
    assertEquals(200, adminRes.getStatusCode().value());
  }

  @Test
  void shouldRejectKycWhenRoleNotDriverOrAdmin() {
    UUID driverId = UUID.randomUUID();
    var kycReq =
        new com.ridehailing.logistics.driver.dto.request.DriverKycRequest(
            "012345678901", java.time.LocalDate.of(1990, 1, 1), "123 Street");

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> controller.submitKyc(driverId, "CUSTOMER", kycReq, "corr-1"));
    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
  }

  @Test
  void shouldRejectAvailabilityWhenRoleNotDriverOrAdmin() {
    UUID driverId = UUID.randomUUID();

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () ->
                controller.updateAvailability(
                    driverId, "", DriverAvailabilityStatus.AVAILABLE, "corr-1"));
    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
  }

  @Test
  void shouldRejectVehicleRegistrationWhenRoleNotDriverOrAdmin() {
    UUID driverId = UUID.randomUUID();
    var vehReq =
        new com.ridehailing.logistics.driver.dto.request.VehicleCreateRequest(
            com.ridehailing.logistics.common.domain.enums.VehicleType.BIKE,
            "29A-12345",
            "Honda",
            "Wave",
            "Red");

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> controller.registerVehicle(driverId, "CUSTOMER", vehReq, "corr-1"));
    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
  }
}
