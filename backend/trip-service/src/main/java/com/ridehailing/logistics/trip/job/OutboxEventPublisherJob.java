package com.ridehailing.logistics.trip.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCancelledEvent;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.trip.domain.entity.OutboxEvent;
import com.ridehailing.logistics.trip.event.TripEventProducer;
import com.ridehailing.logistics.trip.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherJob {

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Scheduled(fixedDelay = 5000)
  @SchedulerLock(name = "outboxEventPublisherJob", lockAtLeastFor = "2s", lockAtMostFor = "1m")
  public void publishPendingOutboxEvents() {
    List<OutboxEvent> pendingEvents =
        outboxEventRepository.findPendingEventsForUpdate(50);

    if (pendingEvents == null || pendingEvents.isEmpty()) {
      return;
    }

    log.info("Found {} pending outbox event(s) to publish", pendingEvents.size());

    for (OutboxEvent event : pendingEvents) {
      try {
        boolean isTripCompleted = "TripCompleted".equals(event.getEventType());
        boolean isTripCancelled = "TripCancelled".equals(event.getEventType());
        String topic =
            isTripCompleted
                ? TripEventProducer.TOPIC_TRIP_COMPLETED
                : isTripCancelled
                    ? TripEventProducer.TOPIC_TRIP_CANCELLED
                    : "trip." + event.getEventType().toLowerCase();

        if (isTripCompleted) {
          EventEnvelope<TripCompletedEvent> envelope =
              objectMapper.readValue(
                  event.getPayload(),
                  new TypeReference<EventEnvelope<TripCompletedEvent>>() {});
          kafkaTemplate.send(topic, event.getAggregateId(), envelope).get(5, TimeUnit.SECONDS);
        } else if (isTripCancelled) {
          EventEnvelope<TripCancelledEvent> envelope =
              objectMapper.readValue(
                  event.getPayload(),
                  new TypeReference<EventEnvelope<TripCancelledEvent>>() {});
          kafkaTemplate.send(topic, event.getAggregateId(), envelope).get(5, TimeUnit.SECONDS);
        } else {
          JsonNode payloadNode = objectMapper.readTree(event.getPayload());
          kafkaTemplate.send(topic, event.getAggregateId(), payloadNode).get(5, TimeUnit.SECONDS);
        }

        event.setStatus("PROCESSED");
        event.setProcessedAt(Instant.now());
        outboxEventRepository.save(event);
        log.info(
            "Successfully published outbox event ID {} (type: {}) to Kafka",
            event.getId(),
            event.getEventType());
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        log.error(
            "Thread interrupted while publishing outbox event ID {}: {}",
            event.getId(),
            ex.getMessage());
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= 5) {
          event.setStatus("FAILED");
          log.error(
              "Outbox event ID {} reached max retry attempts, marking as FAILED", event.getId());
        }
        outboxEventRepository.save(event);
      } catch (Exception ex) {
        log.error(
            "Failed to publish outbox event ID {} (attempt {}): {}",
            event.getId(),
            event.getRetryCount() + 1,
            ex.getMessage());
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= 5) {
          event.setStatus("FAILED");
          log.error(
              "Outbox event ID {} reached max retry attempts, marking as FAILED", event.getId());
        }
        outboxEventRepository.save(event);
      }
    }
  }
}
