package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface TripRepository extends JpaRepository<Trip, UUID> {
  Optional<Trip> findByIdempotencyKey(String idempotencyKey);

  Optional<Trip> findByTripCode(String tripCode);

  List<Trip> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  List<Trip> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

  boolean existsByDriverIdAndStatusIn(UUID driverId, List<TripStatus> statuses);
}
