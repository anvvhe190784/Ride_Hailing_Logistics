package com.ridehailing.logistics.trip.repository;

import com.ridehailing.logistics.trip.domain.entity.OutboxEvent;
import java.time.Instant;
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
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

  @Transactional
  @Query(
      value =
          "SELECT * FROM platform.outbox_events "
              + "WHERE status = 'PENDING' AND retry_count < 5 "
              + "ORDER BY created_at ASC "
              + "LIMIT :limit "
              + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<OutboxEvent> findPendingEventsForUpdate(@Param("limit") int limit);

  Optional<OutboxEvent> findFirstByAggregateIdAndEventTypeAndStatus(
      String aggregateId, String eventType, String status);

  @Modifying
  @Transactional
  @Query(
      "UPDATE OutboxEvent e SET e.status = 'PROCESSED', e.processedAt = :now "
          + "WHERE e.aggregateId = :aggregateId AND e.eventType = :eventType AND e.status = 'PENDING'")
  int markEventAsProcessed(
      @Param("aggregateId") String aggregateId,
      @Param("eventType") String eventType,
      @Param("now") Instant now);
}
