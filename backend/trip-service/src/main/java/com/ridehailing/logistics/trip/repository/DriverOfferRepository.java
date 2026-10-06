package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DriverOfferRepository extends JpaRepository<DriverOffer, UUID> {
    Optional<DriverOffer> findByTripIdAndDriverId(UUID tripId, UUID driverId);
    List<DriverOffer> findByTripId(UUID tripId);
    List<DriverOffer> findByDriverIdAndStatus(UUID driverId, String status);
}
