package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.trip.domain.entity.TripStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistory, Long> {
  List<TripStatusHistory> findByTripIdOrderByCreatedAtAsc(UUID tripId);
}
