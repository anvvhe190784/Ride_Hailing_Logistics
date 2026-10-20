package com.ridehailing.logistics.payment.dto.response;

import com.ridehailing.logistics.payment.domain.entity.Wallet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
    UUID id,
    UUID driverId,
    String currency,
    BigDecimal balance,
    String status,
    Instant createdAt,
    Instant updatedAt) {

  public static WalletResponse fromEntity(Wallet wallet) {
    if (wallet == null) {
      return null;
    }
    return new WalletResponse(
        wallet.getId(),
        wallet.getDriverId(),
        wallet.getCurrency(),
        wallet.getBalance(),
        wallet.getStatus(),
        wallet.getCreatedAt(),
        wallet.getUpdatedAt());
  }
}
