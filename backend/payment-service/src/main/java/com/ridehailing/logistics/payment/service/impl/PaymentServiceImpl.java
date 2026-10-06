package com.ridehailing.logistics.payment.service.impl;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.PaymentStatus;
import com.ridehailing.logistics.common.domain.enums.WalletEntryType;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.payment.domain.entity.Payment;
import com.ridehailing.logistics.payment.domain.entity.Wallet;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import com.ridehailing.logistics.payment.repository.PaymentRepository;
import com.ridehailing.logistics.payment.repository.WalletEntryRepository;
import com.ridehailing.logistics.payment.repository.WalletRepository;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

  private static final BigDecimal COMMISSION_RATE =
      new BigDecimal("0.20"); // 20% platform commission
  private final PaymentRepository paymentRepository;
  private final WalletRepository walletRepository;
  private final WalletEntryRepository walletEntryRepository;

  @Override
  @Transactional
  public Payment processTripPayment(
      UUID tripId,
      UUID customerId,
      UUID driverId,
      BigDecimal amount,
      PaymentMethod method,
      String idempotencyKey) {
    // FR-PAY-003, BR-015: Idempotency check
    Optional<Payment> existingOpt = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existingOpt.isPresent()) {
      log.info(
          "Idempotent payment detected for key {}. Returning existing record.", idempotencyKey);
      return existingOpt.get();
    }

    Payment payment =
        Payment.builder()
            .tripId(tripId)
            .customerId(customerId)
            .amount(amount)
            .provider(method)
            .status(PaymentStatus.SUCCEEDED)
            .providerRef("SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
            .idempotencyKey(idempotencyKey)
            .paidAt(Instant.now())
            .build();

    payment = paymentRepository.save(payment);
    log.info(
        "Processed payment ID {} for Trip ID {}, amount: {} VND", payment.getId(), tripId, amount);

    // FR-WAL-003, BR-012: Credit driver wallet using Immutable Ledger
    if (driverId != null && amount.compareTo(BigDecimal.ZERO) > 0) {
      Wallet wallet =
          walletRepository
              .findByDriverId(driverId)
              .orElseGet(
                  () ->
                      walletRepository.save(
                          Wallet.builder().driverId(driverId).balance(BigDecimal.ZERO).build()));

      BigDecimal commission = amount.multiply(COMMISSION_RATE).setScale(2, RoundingMode.HALF_UP);
      BigDecimal netEarning = amount.subtract(commission).setScale(2, RoundingMode.HALF_UP);
      BigDecimal newBalance = wallet.getBalance().add(netEarning);

      wallet.setBalance(newBalance);
      walletRepository.save(wallet);

      WalletEntry entry =
          WalletEntry.builder()
              .walletId(wallet.getId())
              .entryType(WalletEntryType.TRIP_EARNING)
              .amount(netEarning)
              .balanceAfter(newBalance)
              .referenceType("TRIP")
              .referenceId(tripId.toString())
              .description(
                  "Earning from completed trip "
                      + tripId
                      + " (Fare: "
                      + amount
                      + " - Commission: "
                      + commission
                      + ")")
              .status("POSTED")
              .build();

      walletEntryRepository.save(entry);
      log.info(
          "Recorded wallet ledger entry for driver {}: +{} VND, new balance: {} VND",
          driverId,
          netEarning,
          newBalance);
    }

    return payment;
  }

  @Override
  @Transactional(readOnly = true)
  public Wallet getWalletByDriver(UUID driverId) {
    return findWalletOrThrow(driverId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WalletEntry> getWalletStatement(UUID driverId) {
    Wallet wallet = findWalletOrThrow(driverId);
    return walletEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId());
  }

  private Wallet findWalletOrThrow(UUID driverId) {
    return walletRepository
        .findByDriverId(driverId)
        .orElseThrow(
            () -> BaseDomainException.notFound("WALLET_NOT_FOUND", "Driver wallet not found"));
  }
}
