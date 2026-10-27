package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface TripRepository extends JpaRepository<Trip, UUID> {
  Optional<Trip> findByIdempotencyKey(String idempotencyKey);

  Optional<Trip> findByTripCode(String tripCode);

  boolean existsByQuoteId(UUID quoteId);

  List<Trip> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

  List<Trip> findByDriverIdOrderByCreatedAtDesc(UUID driverId);

  List<Trip> findByStatusAndCreatedAtBefore(TripStatus status, Instant before);

  List<Trip> findByStatusInAndAcceptedAtBefore(List<TripStatus> statuses, Instant before);

  boolean existsByDriverIdAndStatusIn(UUID driverId, List<TripStatus> statuses);

  @Query(
      "SELECT t FROM Trip t WHERE t.status = :status AND (t.inTripAt < :before OR (t.inTripAt IS NULL AND t.startedAt < :before) OR (t.inTripAt IS NULL AND t.startedAt IS NULL AND t.updatedAt < :before))")
  List<Trip> findStaleInTripTrips(@Param("status") TripStatus status, @Param("before") Instant before);

  @Modifying(clearAutomatically = true)
  @Query(
      "UPDATE Trip t SET t.driverId = :driverId, t.status = com.ridehailing.logistics.common.domain.enums.TripStatus.ACCEPTED, t.acceptedAt = :now, t.updatedAt = :now, t.version = t.version + 1 WHERE t.id = :tripId AND t.status = com.ridehailing.logistics.common.domain.enums.TripStatus.MATCHING AND t.driverId IS NULL")
  int claimTripAtomically(
      @Param("tripId") UUID tripId, @Param("driverId") UUID driverId, @Param("now") Instant now);
}
