package com.ridehailing.logistics.payment.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.PaymentStatus;
import com.ridehailing.logistics.common.domain.enums.WalletEntryType;
import com.ridehailing.logistics.payment.domain.entity.Payment;
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
import org.mockito.ArgumentCaptor;
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

  @Test
  @DisplayName("processCancellationPenalty should succeed for cashless and credit driver wallet")
  void shouldProcessCancellationPenaltyForCashlessAndCreditDriverWallet() {
    UUID tripId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    BigDecimal fee = new BigDecimal("15000.00");
    String idempKey = "PENALTY-TRIP-" + tripId;

    when(paymentRepository.findByIdempotencyKey(idempKey)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    when(walletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));

    paymentService.processCancellationPenalty(
        tripId, customerId, driverId, fee, PaymentMethod.WALLET, idempKey);

    ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository, times(1)).save(paymentCaptor.capture());
    Payment savedPayment = paymentCaptor.getValue();
    assertEquals(tripId, savedPayment.getTripId());
    assertEquals(customerId, savedPayment.getCustomerId());
    assertEquals(fee, savedPayment.getAmount());
    assertEquals(PaymentMethod.WALLET, savedPayment.getProvider());
    assertEquals(PaymentStatus.SUCCEEDED, savedPayment.getStatus());
    assertNotNull(savedPayment.getPaidAt());

    assertEquals(new BigDecimal("115000.00"), wallet.getBalance());
    verify(walletRepository, times(1)).save(wallet);

    ArgumentCaptor<WalletEntry> entryCaptor = ArgumentCaptor.forClass(WalletEntry.class);
    verify(walletEntryRepository, times(1)).save(entryCaptor.capture());
    WalletEntry savedEntry = entryCaptor.getValue();
    assertEquals(walletId, savedEntry.getWalletId());
    assertEquals(tripId, savedEntry.getTripId());
    assertEquals(WalletEntryType.PENALTY, savedEntry.getEntryType());
    assertEquals(fee, savedEntry.getAmount());
    assertEquals(new BigDecimal("115000.00"), savedEntry.getBalanceAfter());
    assertEquals("TRIP_CANCELLATION", savedEntry.getReferenceType());
    assertEquals(tripId.toString(), savedEntry.getReferenceId());
    assertEquals("POSTED", savedEntry.getStatus());
  }

  @Test
  @DisplayName("processCancellationPenalty should set status PENDING for CASH payment")
  void shouldProcessCancellationPenaltyForCashWithPendingStatus() {
    UUID tripId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    BigDecimal fee = new BigDecimal("10000.00");
    String idempKey = "PENALTY-TRIP-" + tripId;

    when(paymentRepository.findByIdempotencyKey(idempKey)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    when(walletRepository.findByDriverIdForUpdate(driverId)).thenReturn(Optional.of(wallet));

    paymentService.processCancellationPenalty(
        tripId, customerId, driverId, fee, PaymentMethod.CASH, idempKey);

    ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
    verify(paymentRepository, times(1)).save(paymentCaptor.capture());
    Payment savedPayment = paymentCaptor.getValue();
    assertEquals(PaymentStatus.PENDING, savedPayment.getStatus());
    assertNull(savedPayment.getPaidAt());

    assertEquals(new BigDecimal("110000.00"), wallet.getBalance());
    verify(walletRepository, times(1)).save(wallet);
    verify(walletEntryRepository, times(1)).save(any(WalletEntry.class));
  }

  @Test
  @DisplayName("processCancellationPenalty should skip when idempotency key already exists")
  void shouldSkipWhenIdempotencyKeyAlreadyExists() {
    UUID tripId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    BigDecimal fee = new BigDecimal("10000.00");
    String idempKey = "PENALTY-TRIP-" + tripId;

    Payment existingPayment = Payment.builder().idempotencyKey(idempKey).build();
    when(paymentRepository.findByIdempotencyKey(idempKey)).thenReturn(Optional.of(existingPayment));

    paymentService.processCancellationPenalty(
        tripId, customerId, driverId, fee, PaymentMethod.WALLET, idempKey);

    verify(paymentRepository, never()).save(any(Payment.class));
    verify(walletRepository, never()).save(any(Wallet.class));
    verify(walletEntryRepository, never()).save(any(WalletEntry.class));
  }

  @Test
  @DisplayName("processCancellationPenalty should skip when fee is zero or null")
  void shouldSkipWhenCancellationFeeIsZeroOrNull() {
    UUID tripId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    String idempKey = "PENALTY-TRIP-" + tripId;

    paymentService.processCancellationPenalty(
        tripId, customerId, driverId, BigDecimal.ZERO, PaymentMethod.WALLET, idempKey);
    paymentService.processCancellationPenalty(
        tripId, customerId, driverId, null, PaymentMethod.WALLET, idempKey);

    verify(paymentRepository, never()).findByIdempotencyKey(any());
    verify(paymentRepository, never()).save(any(Payment.class));
  }

  @Test
  @DisplayName("processCancellationPenalty should process payment without crediting wallet when driverId is null")
  void shouldProcessPenaltyWithoutDriverIfDriverIdNull() {
    UUID tripId = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    BigDecimal fee = new BigDecimal("10000.00");
    String idempKey = "PENALTY-TRIP-" + tripId;

    when(paymentRepository.findByIdempotencyKey(idempKey)).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

    paymentService.processCancellationPenalty(
        tripId, customerId, null, fee, PaymentMethod.WALLET, idempKey);

    verify(paymentRepository, times(1)).save(any(Payment.class));
    verify(walletRepository, never()).findByDriverIdForUpdate(any());
    verify(walletEntryRepository, never()).save(any(WalletEntry.class));
  }
}
