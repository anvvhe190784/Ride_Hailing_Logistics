package com.ridehailing.logistics.payment.repository;

import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {
  List<WalletEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
