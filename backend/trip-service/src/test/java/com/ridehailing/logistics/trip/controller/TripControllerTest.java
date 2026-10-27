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

  @Test
  @DisplayName("createTrip should succeed for CUSTOMER role")
  void shouldAllowCustomerToCreateTrip() {
    com.ridehailing.logistics.trip.dto.CreateTripRequest req =
        new com.ridehailing.logistics.trip.dto.CreateTripRequest(
            UUID.randomUUID(), "idemp-key-1", PaymentMethod.CASH);
    when(tripService.createTrip(eq(customerId), eq(req))).thenReturn(tripDetail);

    ResponseEntity<ApiResponse<TripDetailResponse>> response =
        tripController.createTrip(customerId, "CUSTOMER", null, req, "corr-create");

    assertNotNull(response);
    assertEquals(201, response.getStatusCode().value());
    verify(tripService, times(1)).createTrip(customerId, req);
  }

  @Test
  @DisplayName("createTrip should reject non-customer, non-admin role with 403 FORBIDDEN_ROLE")
  void shouldRejectDriverFromCreatingTrip() {
    com.ridehailing.logistics.trip.dto.CreateTripRequest req =
        new com.ridehailing.logistics.trip.dto.CreateTripRequest(
            UUID.randomUUID(), "idemp-key-2", PaymentMethod.CASH);

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> tripController.createTrip(customerId, "DRIVER", null, req, "corr-create-fail"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ROLE", ex.getErrorCode());
    verify(tripService, never()).createTrip(any(), any());
  }

  @Test
  @DisplayName("createTrip should use header Idempotency-Key when provided")
  void shouldUseHeaderIdempotencyKeyWhenPresent() {
    com.ridehailing.logistics.trip.dto.CreateTripRequest req =
        new com.ridehailing.logistics.trip.dto.CreateTripRequest(
            UUID.randomUUID(), "body-key", PaymentMethod.CASH);
    when(tripService.createTrip(eq(customerId), any())).thenReturn(tripDetail);

    tripController.createTrip(customerId, "CUSTOMER", "header-key-override", req, "corr-idemp");

    org.mockito.ArgumentCaptor<com.ridehailing.logistics.trip.dto.CreateTripRequest> captor =
        org.mockito.ArgumentCaptor.forClass(com.ridehailing.logistics.trip.dto.CreateTripRequest.class);
    verify(tripService, times(1)).createTrip(eq(customerId), captor.capture());
    assertEquals("header-key-override", captor.getValue().idempotencyKey());
  }

  @Test
  @DisplayName("updateTripStatus should forward actorRole to tripService")
  void shouldForwardActorRoleToTripService() {
    when(tripService.updateTripStatus(tripId, driverId, "DRIVER", TripStatus.ARRIVED))
        .thenReturn(tripDetail);

    ResponseEntity<ApiResponse<TripDetailResponse>> response =
        tripController.updateTripStatus(tripId, TripStatus.ARRIVED, driverId, "DRIVER", "corr-update");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    verify(tripService, times(1))
        .updateTripStatus(tripId, driverId, "DRIVER", TripStatus.ARRIVED);
  }

  @Test
  @DisplayName("estimateCancellationFee should return cancellation fee preview")
  void shouldReturnCancellationFeePreview() {
    com.ridehailing.logistics.trip.dto.response.CancellationFeeResponse feeResponse =
        new com.ridehailing.logistics.trip.dto.response.CancellationFeeResponse(
            tripId, new BigDecimal("10000.00"), "VND", false, "FR-CAN-003 policy");
    when(tripService.estimateCancellationFee(tripId, customerId, "CUSTOMER"))
        .thenReturn(feeResponse);

    ResponseEntity<ApiResponse<com.ridehailing.logistics.trip.dto.response.CancellationFeeResponse>>
        response =
            tripController.estimateCancellationFee(
                tripId, customerId, "CUSTOMER", "corr-preview");

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(200, response.getStatusCode().value());
    assertEquals(new BigDecimal("10000.00"), response.getBody().getData().cancellationFee());
    verify(tripService, times(1)).estimateCancellationFee(tripId, customerId, "CUSTOMER");
  }
}
