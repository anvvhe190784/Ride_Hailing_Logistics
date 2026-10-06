package com.ridehailing.logistics.payment.controller;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.payment.domain.entity.Wallet;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
  public ResponseEntity<ApiResponse<Wallet>> getMyWallet(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    Wallet wallet = paymentService.getWalletByDriver(driverId);
    return ResponseEntity.ok(ApiResponse.success(wallet, correlationId));
  }

  @GetMapping("/wallets/statement")
  public ResponseEntity<ApiResponse<List<WalletEntry>>> getMyWalletStatement(
      @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
      @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false)
          String correlationId) {
    List<WalletEntry> entries = paymentService.getWalletStatement(driverId);
    return ResponseEntity.ok(ApiResponse.success(entries, correlationId));
  }
}
