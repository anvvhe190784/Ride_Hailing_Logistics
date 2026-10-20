package com.ridehailing.logistics.payment.dto.response;

import com.ridehailing.logistics.common.domain.enums.WalletEntryType;
import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletEntryResponse(
    UUID id,
    UUID walletId,
    WalletEntryType entryType,
    BigDecimal amount,
    BigDecimal balanceAfter,
    String referenceType,
    String referenceId,
    String description,
    String status,
    Instant createdAt) {

  public static WalletEntryResponse fromEntity(WalletEntry entry) {
    if (entry == null) {
      return null;
    }
    return new WalletEntryResponse(
        entry.getId(),
        entry.getWalletId(),
        entry.getEntryType(),
        entry.getAmount(),
        entry.getBalanceAfter(),
        entry.getReferenceType(),
        entry.getReferenceId(),
        entry.getDescription(),
        entry.getStatus(),
        entry.getCreatedAt());
  }
}
