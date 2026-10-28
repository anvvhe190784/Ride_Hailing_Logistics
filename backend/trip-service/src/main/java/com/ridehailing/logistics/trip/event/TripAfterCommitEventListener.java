package com.ridehailing.logistics.trip.event;

import com.ridehailing.logistics.trip.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripAfterCommitEventListener {

  private final TripEventProducer eventProducer;
  private final OutboxEventRepository outboxEventRepository;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleTripCompleted(TripCompletedInternalEvent event) {
    log.info(
        "Handling TripCompletedInternalEvent AFTER_COMMIT for trip ID: {}",
        event.tripId());
    eventProducer.publishTripCompleted(
        event.trip(),
        () -> {
          int updated =
              outboxEventRepository.markEventAsProcessed(
                  event.tripId().toString(), "TripCompleted", Instant.now());
          if (updated > 0) {
            log.info("Marked OutboxEvent PROCESSED after commit for trip ID {}", event.tripId());
          } else {
            outboxEventRepository
                .findFirstByAggregateIdAndEventTypeAndStatus(
                    event.tripId().toString(), "TripCompleted", "PENDING")
                .ifPresent(
                    outbox -> {
                      outbox.setStatus("PROCESSED");
                      outbox.setProcessedAt(Instant.now());
                      outboxEventRepository.save(outbox);
                      log.info(
                          "Marked OutboxEvent PROCESSED via fallback for trip ID {}",
                          event.tripId());
                    });
          }
        });
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleTripCancelled(TripCancelledInternalEvent event) {
    if (event == null || event.trip() == null || event.trip().getId() == null) {
      return;
    }
    UUID tripId = event.trip().getId();
    log.info("Handling TripCancelledInternalEvent AFTER_COMMIT for trip ID: {}", tripId);
    eventProducer.publishTripCancelled(
        event.trip(),
        () -> {
          int updated =
              outboxEventRepository.markEventAsProcessed(
                  tripId.toString(), "TripCancelled", Instant.now());
          if (updated > 0) {
            log.info("Marked OutboxEvent PROCESSED after commit for cancelled trip ID {}", tripId);
          } else {
            outboxEventRepository
                .findFirstByAggregateIdAndEventTypeAndStatus(
                    tripId.toString(), "TripCancelled", "PENDING")
                .ifPresent(
                    outbox -> {
                      outbox.setStatus("PROCESSED");
                      outbox.setProcessedAt(Instant.now());
                      outboxEventRepository.save(outbox);
                      log.info(
                          "Marked OutboxEvent PROCESSED via fallback for cancelled trip ID {}",
                          tripId);
                    });
          }
        });
  }
}
