package com.ridehailing.logistics.payment.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.WalletEntryType;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.payment.domain.entity.Wallet;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import com.ridehailing.logistics.payment.dto.response.WalletEntryResponse;
import com.ridehailing.logistics.payment.dto.response.WalletResponse;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

  @Mock private PaymentService paymentService;

  @InjectMocks private PaymentController paymentController;

  private UUID driverId;
  private Wallet wallet;

  @BeforeEach
  void setUp() {
    driverId = UUID.randomUUID();
    wallet =
        Wallet.builder()
            .id(UUID.randomUUID())
            .driverId(driverId)
            .balance(new BigDecimal("150000"))
            .currency("VND")
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("DRIVER role should be allowed to view their own wallet")
  void shouldAllowDriverToGetMyWallet() {
    when(paymentService.getWalletByDriver(driverId)).thenReturn(wallet);

    ResponseEntity<ApiResponse<WalletResponse>> response =
        paymentController.getMyWallet(driverId, "DRIVER", "corr-1");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(new BigDecimal("150000"), response.getBody().getData().balance());
  }

  @Test
  @DisplayName("ADMIN role should be allowed to view driver wallet")
  void shouldAllowAdminToGetDriverWallet() {
    when(paymentService.getWalletByDriver(driverId)).thenReturn(wallet);

    ResponseEntity<ApiResponse<WalletResponse>> response =
        paymentController.getMyWallet(driverId, "ADMIN", "corr-2");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
  }

  @Test
  @DisplayName("Non-driver (e.g. CUSTOMER) should be rejected with FORBIDDEN_ACCESS")
  void shouldRejectCustomerFromGettingWallet() {
    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> paymentController.getMyWallet(driverId, "CUSTOMER", "corr-3"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
    verify(paymentService, never()).getWalletByDriver(any());
  }

  @Test
  @DisplayName("DRIVER role should be allowed to view wallet statement")
  void shouldAllowDriverToGetWalletStatement() {
    WalletEntry entry =
        WalletEntry.builder()
            .id(UUID.randomUUID())
            .walletId(wallet.getId())
            .amount(new BigDecimal("50000"))
            .entryType(WalletEntryType.TRIP_EARNING)
            .balanceAfter(new BigDecimal("150000"))
            .description("Trip fare")
            .createdAt(Instant.now())
            .build();
    Page<WalletEntry> page = new PageImpl<>(List.of(entry));
    when(paymentService.getWalletStatement(eq(driverId), any())).thenReturn(page);

    ResponseEntity<ApiResponse<Page<WalletEntryResponse>>> response =
        paymentController.getMyWalletStatement(driverId, "DRIVER", PageRequest.of(0, 10), "corr-4");

    assertNotNull(response);
    assertEquals(200, response.getStatusCode().value());
    assertEquals(1, response.getBody().getData().getTotalElements());
  }

  @Test
  @DisplayName("Empty or customer role should be rejected on wallet statement")
  void shouldRejectEmptyRoleFromGettingStatement() {
    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> paymentController.getMyWalletStatement(driverId, "", PageRequest.of(0, 10), "corr-5"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
    verify(paymentService, never()).getWalletStatement(any(), any());
  }
}
