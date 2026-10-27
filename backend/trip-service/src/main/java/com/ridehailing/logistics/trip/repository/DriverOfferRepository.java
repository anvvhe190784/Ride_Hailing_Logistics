package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@SuppressWarnings("unused")
public interface DriverOfferRepository extends JpaRepository<DriverOffer, UUID> {
  Optional<DriverOffer> findByTripIdAndDriverId(UUID tripId, UUID driverId);

  List<DriverOffer> findByTripId(UUID tripId);

  List<DriverOffer> findByDriverIdAndStatus(UUID driverId, String status);

  @Modifying
  @Transactional
  @Query(
      "UPDATE DriverOffer o SET o.status = 'EXPIRED', o.respondedAt = :now "
          + "WHERE o.tripId IN :tripIds AND o.status = 'PENDING'")
  int expirePendingOffersByTripIds(
      @Param("tripIds") Collection<UUID> tripIds, @Param("now") Instant now);

  @Modifying
  @Transactional
  @Query(
      "UPDATE DriverOffer o SET o.status = 'EXPIRED', o.respondedAt = :now "
          + "WHERE o.status = 'PENDING' AND o.expiresAt < :now")
  int expireOverdueOffers(@Param("now") Instant now);
}
