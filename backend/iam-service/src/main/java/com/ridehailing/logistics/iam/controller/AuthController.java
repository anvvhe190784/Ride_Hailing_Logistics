package com.ridehailing.logistics.iam.controller;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.iam.dto.request.LoginRequest;
import com.ridehailing.logistics.iam.dto.request.RefreshTokenRequest;
import com.ridehailing.logistics.iam.dto.request.RegisterRequest;
import com.ridehailing.logistics.iam.dto.response.AuthResponse;
import com.ridehailing.logistics.iam.dto.response.UserResponse;
import com.ridehailing.logistics.iam.service.AuthService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/register")
  public ResponseEntity<ApiResponse<AuthResponse>> register(
      @Valid @RequestBody RegisterRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    AuthResponse response = authService.register(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(response, correlationId));
  }

  @PostMapping("/login")
  public ResponseEntity<ApiResponse<AuthResponse>> login(
      @Valid @RequestBody LoginRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    AuthResponse response = authService.login(request);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @PostMapping("/refresh-token")
  public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
      @Valid @RequestBody RefreshTokenRequest request,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    AuthResponse response = authService.refreshToken(request);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  @GetMapping("/me")
  public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID userId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    UserResponse response = authService.getUserProfile(userId);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }
}
