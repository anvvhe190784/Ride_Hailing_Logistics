package com.ridehailing.logistics.payment.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.WalletEntryType;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
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
  private WalletResponse walletResponse;

  @BeforeEach
  void setUp() {
    driverId = UUID.randomUUID();
    walletResponse =
        new WalletResponse(
            UUID.randomUUID(),
            driverId,
            "VND",
            new BigDecimal("150000"),
            "ACTIVE",
            Instant.now(),
            Instant.now());
  }

  @Test
  @DisplayName("DRIVER role should be allowed to view their own wallet")
  void shouldAllowDriverToGetMyWallet() {
    when(paymentService.getWalletByDriver(driverId)).thenReturn(walletResponse);

    ResponseEntity<ApiResponse<WalletResponse>> response =
        paymentController.getMyWallet(driverId, "DRIVER", "corr-1");

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(200, response.getStatusCode().value());
    assertEquals(new BigDecimal("150000"), response.getBody().getData().balance());
  }

  @Test
  @DisplayName("ADMIN role should be allowed to view driver wallet")
  void shouldAllowAdminToGetDriverWallet() {
    when(paymentService.getWalletByDriver(driverId)).thenReturn(walletResponse);

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
    WalletEntryResponse entryResponse =
        new WalletEntryResponse(
            UUID.randomUUID(),
            walletResponse.id(),
            WalletEntryType.TRIP_EARNING,
            new BigDecimal("50000"),
            new BigDecimal("150000"),
            "TRIP",
            "ref-123",
            "Trip fare",
            "POSTED",
            Instant.now());
    Page<WalletEntryResponse> page = new PageImpl<>(List.of(entryResponse));
    when(paymentService.getWalletStatement(eq(driverId), any())).thenReturn(page);

    ResponseEntity<ApiResponse<Page<WalletEntryResponse>>> response =
        paymentController.getMyWalletStatement(driverId, "DRIVER", PageRequest.of(0, 10), "corr-4");

    assertNotNull(response);
    assertNotNull(response.getBody());
    assertEquals(200, response.getStatusCode().value());
    assertEquals(1, response.getBody().getData().getTotalElements());
  }

  @Test
  @DisplayName("Empty or customer role should be rejected on wallet statement")
  void shouldRejectEmptyRoleFromGettingStatement() {
    org.springframework.data.domain.Pageable pageable = PageRequest.of(0, 10);
    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> paymentController.getMyWalletStatement(driverId, "", pageable, "corr-5"));

    assertEquals(403, ex.getHttpStatus());
    assertEquals("FORBIDDEN_ACCESS", ex.getErrorCode());
    verify(paymentService, never()).getWalletStatement(any(), any());
  }
}
