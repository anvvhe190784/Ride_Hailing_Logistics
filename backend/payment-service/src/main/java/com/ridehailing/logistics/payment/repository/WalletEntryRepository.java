package com.ridehailing.logistics.payment.repository;

import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {
  List<WalletEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

  Page<WalletEntry> findByWalletId(UUID walletId, Pageable pageable);

  Page<WalletEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);
}
