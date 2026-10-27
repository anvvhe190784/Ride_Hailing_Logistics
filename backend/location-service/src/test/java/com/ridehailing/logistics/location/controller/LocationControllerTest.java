package com.ridehailing.logistics.location.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import com.ridehailing.logistics.location.service.LocationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class LocationControllerTest {

  @Mock private LocationService locationService;

  private LocationController controller;

  @BeforeEach
  void setUp() {
    controller = new LocationController(locationService);
  }

  @Test
  void shouldAllowDriverToSubmitTelemetry() {
    UUID driverId = UUID.randomUUID();
    TelemetryRequest req =
        new TelemetryRequest(
            new BigDecimal("21.028511"),
            new BigDecimal("105.854444"),
            5.0,
            90.0,
            12.0,
            1L,
            Instant.now());

    ResponseEntity<ApiResponse<Void>> response =
        controller.submitTelemetry(driverId, "DRIVER", req, "corr-1");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    verify(locationService, times(1)).processTelemetry(driverId, req);
  }

  @Test
  void shouldRejectTelemetryWhenRoleIsNotDriverOrAdmin() {
    UUID driverId = UUID.randomUUID();
    TelemetryRequest req =
        new TelemetryRequest(
            new BigDecimal("21.028511"),
            new BigDecimal("105.854444"),
            5.0,
            90.0,
            12.0,
            1L,
            Instant.now());

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> controller.submitTelemetry(driverId, "PASSENGER", req, "corr-1"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
    verifyNoInteractions(locationService);
  }

  @Test
  void shouldGetNearbyDrivers() {
    UUID driverId = UUID.randomUUID();
    List<NearbyDriverResponse> mockDrivers =
        List.of(new NearbyDriverResponse(driverId, 21.028511, 105.854444, 250.0));
    when(locationService.findNearbyDrivers(21.028511, 105.854444, 3000.0, 10))
        .thenReturn(mockDrivers);

    ResponseEntity<ApiResponse<List<NearbyDriverResponse>>> response =
        controller.getNearbyDrivers(21.028511, 105.854444, 3000.0, 10, "corr-2");

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(200, response.getStatusCode().value());
    assertEquals(1, response.getBody().getData().size());
  }
}
