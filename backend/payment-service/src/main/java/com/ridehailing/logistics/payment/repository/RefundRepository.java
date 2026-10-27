package com.ridehailing.logistics.payment.repository;

import com.ridehailing.logistics.payment.domain.entity.Refund;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {
  List<Refund> findByPaymentId(UUID paymentId);

  List<Refund> findByTripId(UUID tripId);
}
