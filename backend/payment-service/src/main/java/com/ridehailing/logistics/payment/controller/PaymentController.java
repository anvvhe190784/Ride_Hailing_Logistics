package com.ridehailing.logistics.payment.controller;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.payment.domain.entity.Wallet;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import com.ridehailing.logistics.payment.dto.response.WalletEntryResponse;
import com.ridehailing.logistics.payment.dto.response.WalletResponse;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

  private final PaymentService paymentService;

  @GetMapping("/wallets/me")
  public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_USER_ROLE, defaultValue = "") String role,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    validateDriverOrAdmin(role);
    Wallet wallet = paymentService.getWalletByDriver(driverId);
    return ResponseEntity.ok(ApiResponse.success(WalletResponse.fromEntity(wallet), correlationId));
  }

  @GetMapping("/wallets/statement")
  public ResponseEntity<ApiResponse<Page<WalletEntryResponse>>> getMyWalletStatement(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_USER_ROLE, defaultValue = "") String role,
      @PageableDefault(size = 20) Pageable pageable,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    validateDriverOrAdmin(role);
    Page<WalletEntry> entries = paymentService.getWalletStatement(driverId, pageable);
    Page<WalletEntryResponse> response = entries.map(WalletEntryResponse::fromEntity);
    return ResponseEntity.ok(ApiResponse.success(response, correlationId));
  }

  private void validateDriverOrAdmin(String role) {
    if (role == null
        || (!"DRIVER".equalsIgnoreCase(role.trim()) && !"ADMIN".equalsIgnoreCase(role.trim()))) {
      throw BaseDomainException.forbidden(
          "FORBIDDEN_ACCESS", "Access denied: DRIVER or ADMIN role required");
    }
  }
}
