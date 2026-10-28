package com.ridehailing.logistics.payment.service;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.payment.dto.response.WalletEntryResponse;
import com.ridehailing.logistics.payment.dto.response.WalletResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentService {
  void processTripPayment(
      UUID tripId,
      UUID customerId,
      UUID driverId,
      BigDecimal amount,
      PaymentMethod method,
      String idempotencyKey);

  void processCancellationPenalty(
      UUID tripId,
      UUID customerId,
      UUID driverId,
      BigDecimal cancellationFee,
      PaymentMethod paymentMethod,
      String idempotencyKey);

  WalletResponse getWalletByDriver(UUID driverId);

  List<WalletEntryResponse> getWalletStatement(UUID driverId);

  Page<WalletEntryResponse> getWalletStatement(UUID driverId, Pageable pageable);
}
