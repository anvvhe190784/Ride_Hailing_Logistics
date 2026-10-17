package com.ridehailing.logistics.payment.repository;

import com.ridehailing.logistics.payment.domain.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface WalletRepository extends JpaRepository<Wallet, UUID> {
  Optional<Wallet> findByDriverId(UUID driverId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT w FROM Wallet w WHERE w.driverId = :driverId")
  Optional<Wallet> findByDriverIdForUpdate(@Param("driverId") UUID driverId);
}
