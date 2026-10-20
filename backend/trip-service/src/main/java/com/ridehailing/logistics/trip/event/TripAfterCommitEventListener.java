package com.ridehailing.logistics.trip.event;

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

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleTripCompleted(TripCompletedInternalEvent event) {
    log.info(
        "Handling TripCompletedInternalEvent AFTER_COMMIT for trip ID: {}",
        event.trip().getId());
    eventProducer.publishTripCompleted(event.trip());
  }
}
