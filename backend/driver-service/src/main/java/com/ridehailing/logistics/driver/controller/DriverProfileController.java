package com.ridehailing.logistics.driver.controller;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.driver.dto.request.DriverKycRequest;
import com.ridehailing.logistics.driver.dto.request.VehicleCreateRequest;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.driver.dto.response.VehicleResponse;
import com.ridehailing.logistics.driver.service.DriverProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverProfileController {

  private final DriverProfileService driverProfileService;

  @PostMapping("/kyc")
  public ResponseEntity<ApiResponse<DriverProfileResponse>> submitKyc(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @Valid @RequestBody DriverKycRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    DriverProfileResponse response = driverProfileService.submitKyc(driverId, request);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @PutMapping("/availability")
  public ResponseEntity<ApiResponse<DriverProfileResponse>> updateAvailability(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestParam DriverAvailabilityStatus status,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    DriverProfileResponse response = driverProfileService.updateAvailability(driverId, status);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<DriverProfileResponse>> getMyProfile(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    DriverProfileResponse response = driverProfileService.getProfile(driverId);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @GetMapping("/{driverId}")
  public ResponseEntity<ApiResponse<DriverProfileResponse>> getDriverById(
      @PathVariable UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    DriverProfileResponse response = driverProfileService.getProfile(driverId);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @PutMapping("/{driverId}/approve")
  public ResponseEntity<ApiResponse<DriverProfileResponse>> approveDriver(
      @PathVariable UUID driverId,
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID reviewerId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    DriverProfileResponse response = driverProfileService.approveDriver(driverId, reviewerId);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @PostMapping("/vehicles")
  public ResponseEntity<ApiResponse<VehicleResponse>> registerVehicle(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @Valid @RequestBody VehicleCreateRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    VehicleResponse response = driverProfileService.registerVehicle(driverId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, correlationId));
  }

  @GetMapping("/vehicles")
  public ResponseEntity<ApiResponse<List<VehicleResponse>>> getMyVehicles(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    List<VehicleResponse> response = driverProfileService.getVehicles(driverId);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }
}
