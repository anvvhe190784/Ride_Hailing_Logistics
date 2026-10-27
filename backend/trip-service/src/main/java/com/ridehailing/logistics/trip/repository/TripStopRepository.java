package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.trip.domain.entity.TripStop;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
  List<TripStop> findByTripIdOrderByStopSequenceAsc(UUID tripId);
}
