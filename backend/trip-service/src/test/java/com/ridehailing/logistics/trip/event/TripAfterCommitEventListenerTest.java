package com.ridehailing.logistics.trip.event;

import static org.mockito.Mockito.*;

import com.ridehailing.logistics.trip.domain.entity.Trip;
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

  @InjectMocks private TripAfterCommitEventListener listener;

  @Test
  @DisplayName("handleTripCompleted should forward trip to eventProducer after commit")
  void shouldForwardTripToProducer() {
    Trip trip = Trip.builder().id(UUID.randomUUID()).tripCode("TRP-998877").build();
    TripCompletedInternalEvent event = new TripCompletedInternalEvent(trip);

    listener.handleTripCompleted(event);

    verify(eventProducer, times(1)).publishTripCompleted(trip);
  }
}
