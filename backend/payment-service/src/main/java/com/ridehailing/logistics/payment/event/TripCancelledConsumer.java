package com.ridehailing.logistics.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCancelledEvent;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripCancelledConsumer {

  private static final String FIELD_PAYLOAD = "payload";
  private static final String FIELD_CORRELATION_ID = "correlationId";

  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;

  private record ExtractedMessage(TripCancelledEvent event, String correlationId) {}

  @KafkaListener(topics = "trip.cancelled", groupId = "payment-group")
  public void consumeTripCancelled(Object rawMessage) {
    String correlationId = null;
    try {
      ExtractedMessage extracted = extractEventAndCorrelationId(rawMessage);
      correlationId = extracted.correlationId();
      if (correlationId != null && !correlationId.isBlank()) {
        MDC.put(FIELD_CORRELATION_ID, correlationId);
      }
      TripCancelledEvent event = extracted.event();

      log.info("Processing trip.cancelled event for tripId={}", event.getTripId());

      String idempotencyKey = "PENALTY-TRIP-" + event.getTripId();
      paymentService.processCancellationPenalty(
          event.getTripId(),
          event.getCustomerId(),
          event.getDriverId(),
          event.getCancellationFee(),
          event.getPaymentMethod(),
          idempotencyKey);
      log.info(
          "Successfully processed cancellation penalty for cancelled Trip ID: {}",
          event.getTripId());
    } catch (Exception e) {
      log.error("Error processing trip.cancelled Kafka event: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to process trip.cancelled event: " + e.getMessage(), e);
    } finally {
      if (correlationId != null) {
        MDC.remove(FIELD_CORRELATION_ID);
      }
    }
  }

  private ExtractedMessage extractEventAndCorrelationId(Object rawMessage)
      throws JsonProcessingException {
    if (rawMessage instanceof ConsumerRecord<?, ?> consumerRecord) {
      rawMessage = consumerRecord.value();
    }
    String correlationId = null;
    TripCancelledEvent event;

    switch (rawMessage) {
      case EventEnvelope<?> envelope -> {
        correlationId = envelope.getCorrelationId();
        if (envelope.getPayload() instanceof TripCancelledEvent directEvent) {
          event = directEvent;
        } else {
          event = objectMapper.convertValue(envelope.getPayload(), TripCancelledEvent.class);
        }
      }
      case Map<?, ?> map -> {
        Object corrObj = map.get(FIELD_CORRELATION_ID);
        if (corrObj != null && !corrObj.toString().isBlank()) {
          correlationId = corrObj.toString();
        }
        Object payload = map.get(FIELD_PAYLOAD);
        event = objectMapper.convertValue(payload != null ? payload : map, TripCancelledEvent.class);
      }
      case TripCancelledEvent directEvent -> event = directEvent;
      case null -> throw new IllegalArgumentException("Received null message on trip.cancelled");
      default -> {
        JsonNode rootNode =
            (rawMessage instanceof String str)
                ? objectMapper.readTree(str)
                : objectMapper.valueToTree(rawMessage);
        if (rootNode != null && rootNode.hasNonNull(FIELD_CORRELATION_ID)) {
          correlationId = rootNode.get(FIELD_CORRELATION_ID).asText();
        }
        if (rootNode != null && rootNode.has(FIELD_PAYLOAD) && !rootNode.get(FIELD_PAYLOAD).isNull()) {
          event = objectMapper.treeToValue(rootNode.get(FIELD_PAYLOAD), TripCancelledEvent.class);
        } else {
          event = objectMapper.convertValue(rawMessage, TripCancelledEvent.class);
        }
      }
    }
    return new ExtractedMessage(event, correlationId);
  }

  public void onTripCancelled(Object rawMessage) {
    consumeTripCancelled(rawMessage);
  }
}
