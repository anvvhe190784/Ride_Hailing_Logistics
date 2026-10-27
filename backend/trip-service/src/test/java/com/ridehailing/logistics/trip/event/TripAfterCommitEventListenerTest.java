package com.ridehailing.logistics.trip.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripAfterCommitEventListenerTest {

  @Mock private TripEventProducer eventProducer;
  @Mock private OutboxEventRepository outboxEventRepository;

  @InjectMocks private TripAfterCommitEventListener listener;

  @Test
  @DisplayName("handleTripCompleted should forward trip to eventProducer after commit")
  void shouldForwardTripToProducer() {
    Trip trip = Trip.builder().id(UUID.randomUUID()).tripCode("TRP-998877").build();
    TripCompletedInternalEvent event = new TripCompletedInternalEvent(trip);

    listener.handleTripCompleted(event);

    verify(eventProducer, times(1)).publishTripCompleted(eq(trip), any());
  }

  @Test
  @DisplayName("handleTripCompleted callback should mark outbox event as PROCESSED")
  void shouldMarkOutboxEventAsProcessedOnCallback() {
    UUID tripId = UUID.randomUUID();
    Trip trip = Trip.builder().id(tripId).tripCode("TRP-998877").build();
    TripCompletedInternalEvent event = new TripCompletedInternalEvent(trip);

    doAnswer(
            invocation -> {
              Runnable callback = invocation.getArgument(1);
              if (callback != null) {
                callback.run();
              }
              return null;
            })
        .when(eventProducer)
        .publishTripCompleted(eq(trip), any());

    when(outboxEventRepository.markEventAsProcessed(
            eq(tripId.toString()), eq("TripCompleted"), any(Instant.class)))
        .thenReturn(1);

    listener.handleTripCompleted(event);

    verify(outboxEventRepository, times(1))
        .markEventAsProcessed(eq(tripId.toString()), eq("TripCompleted"), any(Instant.class));
  }
}
