package com.ridehailing.logistics.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripCompletedConsumer {

  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;

  @KafkaListener(topics = "trip.completed", groupId = "payment-group")
  public void onTripCompleted(Object rawMessage) {
    log.info("Received Kafka message on topic trip.completed: {}", rawMessage);
    try {
      TripCompletedEvent event =
          switch (rawMessage) {
            case EventEnvelope<?> envelope ->
                objectMapper.convertValue(envelope.getPayload(), TripCompletedEvent.class);
            case Map<?, ?> map -> {
              Object payload = map.get("payload");
              yield objectMapper.convertValue(
                  payload != null ? payload : map, TripCompletedEvent.class);
            }
            case null, default -> objectMapper.convertValue(rawMessage, TripCompletedEvent.class);
          };

      String idempotencyKey = "PAY-TRIP-" + event.getTripId();
      paymentService.processTripPayment(
          event.getTripId(),
          event.getCustomerId(),
          event.getDriverId(),
          event.getFinalFare(),
          event.getPaymentMethod(),
          idempotencyKey);
      log.info(
          "Successfully processed payment and ledger for completed Trip ID: {}", event.getTripId());
    } catch (Exception e) {
      log.error("Error processing trip.completed Kafka event: {}", e.getMessage(), e);
    }
  }
}
