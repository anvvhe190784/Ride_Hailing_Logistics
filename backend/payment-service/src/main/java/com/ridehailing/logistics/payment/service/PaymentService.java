package com.ridehailing.logistics.payment.service;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.payment.domain.entity.Payment;
import com.ridehailing.logistics.payment.domain.entity.Wallet;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PaymentService {
  Payment processTripPayment(
      UUID tripId,
      UUID customerId,
      UUID driverId,
      BigDecimal amount,
      PaymentMethod method,
      String idempotencyKey);

  Wallet getWalletByDriver(UUID driverId);

  List<WalletEntry> getWalletStatement(UUID driverId);
}
