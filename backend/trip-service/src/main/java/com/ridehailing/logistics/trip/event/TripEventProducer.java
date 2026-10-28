package com.ridehailing.logistics.trip.event;

import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCancelledEvent;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventProducer {

  public static final String TOPIC_TRIP_COMPLETED = "trip.completed";
  public static final String TOPIC_TRIP_CANCELLED = "trip.cancelled";
  private final KafkaTemplate<String, Object> kafkaTemplate;

  public void publishTripCompleted(Trip trip) {
    publishTripCompleted(trip, null);
  }

  public void publishTripCompleted(Trip trip, Runnable onSuccess) {
    TripCompletedEvent event =
        TripCompletedEvent.builder()
            .tripId(trip.getId())
            .tripCode(trip.getTripCode())
            .customerId(trip.getCustomerId())
            .driverId(trip.getDriverId())
            .serviceType(trip.getServiceType())
            .finalFare(trip.getFinalFare() != null ? trip.getFinalFare() : BigDecimal.ZERO)
            .currency(trip.getCurrency())
            .paymentMethod(trip.getPaymentMethod())
            .completedAt(trip.getCompletedAt() != null ? trip.getCompletedAt() : Instant.now())
            .build();
    String correlationId =
        Optional.ofNullable(MDC.get("correlationId"))
            .filter(cid -> !cid.isBlank())
            .orElseGet(() -> UUID.randomUUID().toString());
    publishTripCompleted(event, correlationId, onSuccess);
  }

  public void publishTripCompleted(TripCompletedEvent event, String correlationId) {
    publishTripCompleted(event, correlationId, null);
  }

  public void publishTripCompleted(
      TripCompletedEvent event, String correlationId, Runnable onSuccess) {
    String effectiveCorrelationId =
        (correlationId != null && !correlationId.isBlank())
            ? correlationId
            : Optional.ofNullable(MDC.get("correlationId"))
                .filter(cid -> !cid.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    EventEnvelope<TripCompletedEvent> envelope =
        EventEnvelope.of("TripCompleted", "trip-service", effectiveCorrelationId, event);
    kafkaTemplate
        .send(TOPIC_TRIP_COMPLETED, event.getTripId().toString(), envelope)
        .whenComplete(
            (result, ex) -> {
              if (ex == null) {
                log.info(
                    "Published TripCompleted event for Trip ID: {} to topic: {}",
                    event.getTripId(),
                    TOPIC_TRIP_COMPLETED);
                if (onSuccess != null) {
                  try {
                    onSuccess.run();
                  } catch (Exception e) {
                    log.warn("Error running outbox completion callback for trip {}: {}", event.getTripId(), e.getMessage());
                  }
                }
              } else {
                log.error(
                    "Failed to publish TripCompleted event for Trip ID: {}: {}",
                    event.getTripId(),
                    ex.getMessage());
              }
            });
  }

  public void publishTripCancelled(Trip trip) {
    publishTripCancelled(trip, null);
  }

  public void publishTripCancelled(Trip trip, Runnable onSuccess) {
    TripCancelledEvent event =
        TripCancelledEvent.builder()
            .tripId(trip.getId())
            .tripCode(trip.getTripCode())
            .customerId(trip.getCustomerId())
            .driverId(trip.getDriverId())
            .cancellationFee(
                trip.getCancellationFee() != null ? trip.getCancellationFee() : BigDecimal.ZERO)
            .currency(trip.getCurrency())
            .paymentMethod(trip.getPaymentMethod())
            .cancelledBy(trip.getCancelledBy())
            .cancellationReason(trip.getCancellationReason())
            .cancelledAt(trip.getCancelledAt() != null ? trip.getCancelledAt() : Instant.now())
            .build();
    String correlationId =
        Optional.ofNullable(MDC.get("correlationId"))
            .filter(cid -> !cid.isBlank())
            .orElseGet(() -> UUID.randomUUID().toString());
    publishTripCancelled(event, correlationId, onSuccess);
  }

  public void publishTripCancelled(TripCancelledEvent event, String correlationId) {
    publishTripCancelled(event, correlationId, null);
  }

  public void publishTripCancelled(
      TripCancelledEvent event, String correlationId, Runnable onSuccess) {
    String effectiveCorrelationId =
        (correlationId != null && !correlationId.isBlank())
            ? correlationId
            : Optional.ofNullable(MDC.get("correlationId"))
                .filter(cid -> !cid.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());
    EventEnvelope<TripCancelledEvent> envelope =
        EventEnvelope.of("TripCancelled", "trip-service", effectiveCorrelationId, event);
    kafkaTemplate
        .send(TOPIC_TRIP_CANCELLED, event.getTripId().toString(), envelope)
        .whenComplete(
            (result, ex) -> {
              if (ex == null) {
                log.info(
                    "Published TripCancelled event for Trip ID: {} to topic: {}",
                    event.getTripId(),
                    TOPIC_TRIP_CANCELLED);
                if (onSuccess != null) {
                  try {
                    onSuccess.run();
                  } catch (Exception e) {
                    log.warn(
                        "Error running outbox completion callback for trip {}: {}",
                        event.getTripId(),
                        e.getMessage());
                  }
                }
              } else {
                log.error(
                    "Failed to publish TripCancelled event for Trip ID: {}: {}",
                    event.getTripId(),
                    ex.getMessage());
              }
            });
  }
}
