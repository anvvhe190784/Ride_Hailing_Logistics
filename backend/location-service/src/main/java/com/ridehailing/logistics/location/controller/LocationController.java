package com.ridehailing.logistics.location.controller;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import com.ridehailing.logistics.location.service.LocationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

  private final LocationService locationService;

  @PostMapping({"/telemetry", "/update"})
  public ResponseEntity<ApiResponse<Void>> submitTelemetry(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_USER_ROLE, defaultValue = "") String role,
      @Valid @RequestBody TelemetryRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    if (!"DRIVER".equalsIgnoreCase(role.trim()) && !"ADMIN".equalsIgnoreCase(role.trim())) {
      throw BaseDomainException.forbidden(
          "FORBIDDEN_ACCESS", "Access denied: DRIVER or ADMIN role required for telemetry");
    }
    locationService.processTelemetry(driverId, request);
    return ResponseEntity.ok(ApiResponse.success(null, correlationId));
  }

  @GetMapping({"/nearby-drivers", "/nearby"})
  public ResponseEntity<ApiResponse<List<NearbyDriverResponse>>> getNearbyDrivers(
      @RequestParam double latitude,
      @RequestParam double longitude,
      @RequestParam(defaultValue = "3000") double radiusMeters,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    List<NearbyDriverResponse> drivers =
        locationService.findNearbyDrivers(latitude, longitude, radiusMeters, limit);
    return ResponseEntity.ok(ApiResponse.success(drivers, correlationId));
  }
}
