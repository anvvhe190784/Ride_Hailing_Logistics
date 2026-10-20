package com.ridehailing.logistics.trip.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.trip.service.TripService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TripControllerTest {

  @Mock private TripService tripService;

  @InjectMocks private TripController tripController;

  private UUID tripId;
  private UUID customerId;
  private UUID driverId;
  private TripDetailResponse tripDetail;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    driverId = UUID.randomUUID();
    tripDetail =
        new TripDetailResponse(
            tripId,
            "TRIP-12345",
            ServiceType.RIDE,
            customerId,
            driverId,
            TripStatus.IN_TRIP,
            UUID.randomUUID(),
            new BigDecimal("60000"),
            "VND",
            PaymentMethod.CASH,
            Instant.now(),
            null);
  }

  @Test
  @DisplayName("Customer who owns the trip should be authorized to view it")
  void shouldAllowCustomerToViewTheirTrip() {
    when(tripService.getTrip(tripId)).thenReturn(tripDetail);

    ResponseEntity<ApiResponse<TripDetailResponse>> response =
        tripController.getTrip(tripId, customerId, "CUSTOMER", "corr-1");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(tripId, response.getBody().getData().tripId());
  }

  @Test
  @DisplayName("Assigned driver should be authorized to view the trip")
  void shouldAllowDriverToViewAssignedTrip() {
    when(tripService.getTrip(tripId)).thenReturn(tripDetail);

    ResponseEntity<ApiResponse<TripDetailResponse>> response =
        tripController.getTrip(tripId, driverId, "DRIVER", "corr-2");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(tripId, response.getBody().getData().tripId());
  }

  @Test
  @DisplayName("ADMIN role should be authorized to view any trip")
  void shouldAllowAdminToViewAnyTrip() {
    UUID adminUserId = UUID.randomUUID();
    when(tripService.getTrip(tripId)).thenReturn(tripDetail);

    ResponseEntity<ApiResponse<TripDetailResponse>> response =
        tripController.getTrip(tripId, adminUserId, "ADMIN", "corr-3");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(tripId, response.getBody().getData().tripId());
  }

  @Test
  @DisplayName("Unauthorized third-party user should be rejected with 403 FORBIDDEN_ACCESS")
  void shouldRejectUnauthorizedUserWithForbiddenAccess() {
    UUID strangerId = UUID.randomUUID();
    when(tripService.getTrip(tripId)).thenReturn(tripDetail);

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> tripController.getTrip(tripId, strangerId, "CUSTOMER", "corr-4"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
    assertEquals("You are not authorized to view this trip", ex.getMessage());
  }
}
