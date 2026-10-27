package com.ridehailing.logistics.iam.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.iam.dto.request.LogoutRequest;
import com.ridehailing.logistics.iam.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  @Test
  @DisplayName("POST /logout should invoke authService.logout and return 200 OK")
  void shouldInvokeLogoutSuccessfully() {
    LogoutRequest request = new LogoutRequest("sample-refresh-token");

    ResponseEntity<ApiResponse<Void>> response =
        authController.logout(request, "Bearer test-access-token", "corr-logout-1");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals("SUCCESS", response.getBody().getCode());
    verify(authService, times(1)).logout(request, "Bearer test-access-token");
  }

  @Test
  @DisplayName("POST /logout backward-compatible method should invoke authService.logout")
  void shouldInvokeLogoutBackwardCompatible() {
    LogoutRequest request = new LogoutRequest("sample-refresh-token");

    ResponseEntity<ApiResponse<Void>> response = authController.logout(request, "corr-logout-2");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    verify(authService, times(1)).logout(request, null);
  }
}
