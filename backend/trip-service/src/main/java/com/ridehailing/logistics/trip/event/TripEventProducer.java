package com.ridehailing.logistics.trip.event;

import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventProducer {

  public static final String TOPIC_TRIP_COMPLETED = "trip.completed";
  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void publishTripCompleted(TripCompletedEvent event, String correlationId) {
    EventEnvelope<TripCompletedEvent> envelope =
        EventEnvelope.of("TripCompleted", "trip-service", correlationId, event);
    kafkaTemplate
        .send(TOPIC_TRIP_COMPLETED, event.getTripId().toString(), envelope)
        .whenComplete(
            (result, ex) -> {
              if (ex == null) {
                log.info(
                    "Published TripCompleted event for Trip ID: {} to topic: {}",
                    event.getTripId(),
                    TOPIC_TRIP_COMPLETED);
              } else {
                log.error(
                    "Failed to publish TripCompleted event for Trip ID: {}: {}",
                    event.getTripId(),
                    ex.getMessage());
              }
            });
  }
}
