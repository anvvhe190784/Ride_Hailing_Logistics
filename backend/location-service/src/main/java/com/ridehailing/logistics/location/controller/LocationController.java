package com.ridehailing.logistics.location.controller;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import com.ridehailing.logistics.location.service.LocationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

  private final LocationService locationService;

  @PostMapping("/telemetry")
  public ResponseEntity<ApiResponse<Void>> submitTelemetry(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @Valid @RequestBody TelemetryRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    locationService.processTelemetry(driverId, request);
    return ResponseEntity.ok(ApiResponse.success(null, correlationId));
  }

  @GetMapping("/nearby-drivers")
  public ResponseEntity<ApiResponse<List<NearbyDriverResponse>>> getNearbyDrivers(
      @RequestParam double latitude,
      @RequestParam double longitude,
      @RequestParam(defaultValue = "3000") double radiusMeters,
      @RequestParam(defaultValue = "10") int limit,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(latitude, longitude, radiusMeters, limit);
    return ResponseEntity.ok(ApiResponse.success(drivers, correlationId));
  }
}
