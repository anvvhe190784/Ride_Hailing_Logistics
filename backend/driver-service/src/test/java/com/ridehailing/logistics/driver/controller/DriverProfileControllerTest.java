package com.ridehailing.logistics.driver.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.DriverReviewStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.driver.service.DriverProfileService;
import java.math.BigDecimal;
import java.time.Instant;
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
}
