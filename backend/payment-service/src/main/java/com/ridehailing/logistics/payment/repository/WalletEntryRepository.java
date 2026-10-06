package com.ridehailing.logistics.payment.repository;

import com.ridehailing.logistics.payment.domain.entity.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {
    List<WalletEntry> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
