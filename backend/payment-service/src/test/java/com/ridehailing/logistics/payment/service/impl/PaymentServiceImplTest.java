package com.ridehailing.logistics.payment.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.WalletEntryType;
import com.ridehailing.logistics.payment.domain.entity.Wallet;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import com.ridehailing.logistics.payment.dto.response.WalletEntryResponse;
import com.ridehailing.logistics.payment.dto.response.WalletResponse;
import com.ridehailing.logistics.payment.repository.PaymentRepository;
import com.ridehailing.logistics.payment.repository.WalletEntryRepository;
import com.ridehailing.logistics.payment.repository.WalletRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

  @Mock private PaymentRepository paymentRepository;
  @Mock private WalletRepository walletRepository;
  @Mock private WalletEntryRepository walletEntryRepository;

  @InjectMocks private PaymentServiceImpl paymentService;

  private UUID driverId;
  private UUID walletId;
  private Wallet wallet;

  @BeforeEach
  void setUp() {
    driverId = UUID.randomUUID();
    walletId = UUID.randomUUID();
    wallet =
        Wallet.builder().id(walletId).driverId(driverId).balance(new BigDecimal("100000")).build();
  }

  @Test
  @DisplayName("getWalletStatement with Pageable queries repository with pagination")
  void shouldReturnPaginatedWalletStatement() {
    when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

    Pageable pageable = PageRequest.of(0, 10);
    WalletEntry entry =
        WalletEntry.builder()
            .id(UUID.randomUUID())
            .walletId(walletId)
            .entryType(WalletEntryType.TRIP_EARNING_CREDIT)
            .amount(new BigDecimal("40000"))
            .balanceAfter(new BigDecimal("100000"))
            .createdAt(Instant.now())
            .build();

    Page<WalletEntry> expectedPage = new PageImpl<>(List.of(entry), pageable, 1);
    when(walletEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId, pageable))
        .thenReturn(expectedPage);

    Page<WalletEntryResponse> result = paymentService.getWalletStatement(driverId, pageable);

    assertNotNull(result);
    assertEquals(1, result.getTotalElements());
    assertEquals(entry.getId(), result.getContent().get(0).id());
    assertEquals(entry.getAmount(), result.getContent().get(0).amount());
    verify(walletEntryRepository, times(1)).findByWalletIdOrderByCreatedAtDesc(walletId, pageable);
  }

  @Test
  @DisplayName("getWalletByDriver returns WalletResponse DTO")
  void shouldReturnWalletResponse() {
    when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

    WalletResponse result = paymentService.getWalletByDriver(driverId);

    assertNotNull(result);
    assertEquals(walletId, result.id());
    assertEquals(driverId, result.driverId());
  }

  @Test
  @DisplayName("getWalletStatement list returns List<WalletEntryResponse>")
  void shouldReturnWalletStatementList() {
    when(walletRepository.findByDriverId(driverId)).thenReturn(Optional.of(wallet));

    WalletEntry entry =
        WalletEntry.builder()
            .id(UUID.randomUUID())
            .walletId(walletId)
            .entryType(WalletEntryType.TRIP_EARNING_CREDIT)
            .amount(new BigDecimal("40000"))
            .balanceAfter(new BigDecimal("100000"))
            .createdAt(Instant.now())
            .build();

    when(walletEntryRepository.findByWalletIdOrderByCreatedAtDesc(walletId))
        .thenReturn(List.of(entry));

    List<WalletEntryResponse> result = paymentService.getWalletStatement(driverId);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals(entry.getId(), result.get(0).id());
  }

  @Test
  @DisplayName(
      "getOrCreateWallet returns existing wallet when findByDriverIdForUpdate returns present")
  void shouldReturnExistingWallet() {
    when(walletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));

    Wallet result = paymentService.getOrCreateWallet(driverId);

    assertNotNull(result);
    assertEquals(driverId, result.getDriverId());
    verify(walletRepository, never()).save(any(Wallet.class));
  }

  @Test
  @DisplayName("getOrCreateWallet inserts wallet if not exists and fetches it")
  void shouldRecoverWalletOnDataIntegrityViolation() {
    // 1st lookup: empty
    when(walletRepository.findByDriverIdForUpdate(driverId))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(wallet)); // 2nd lookup returns the created wallet

    doNothing().when(walletRepository).insertWalletIfNotExists(driverId);

    Wallet result = paymentService.getOrCreateWallet(driverId);

    assertNotNull(result);
    assertEquals(walletId, result.getId());
    assertEquals(driverId, result.getDriverId());
    verify(walletRepository).insertWalletIfNotExists(driverId);
  }
}
