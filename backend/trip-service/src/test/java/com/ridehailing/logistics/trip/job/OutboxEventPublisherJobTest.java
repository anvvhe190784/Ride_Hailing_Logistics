package com.ridehailing.logistics.trip.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCancelledEvent;
import com.ridehailing.logistics.trip.domain.entity.OutboxEvent;
import com.ridehailing.logistics.trip.event.TripEventProducer;
import com.ridehailing.logistics.trip.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherJobTest {

  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private KafkaTemplate<String, Object> kafkaTemplate;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private OutboxEventPublisherJob publisherJob;

  @BeforeEach
  void setUp() {
    objectMapper.findAndRegisterModules();
  }

  @Test
  @DisplayName("should publish TripCancelled event to trip.cancelled topic and mark outbox as PROCESSED")
  void shouldPublishTripCancelledEvent() throws Exception {
    UUID tripId = UUID.randomUUID();
    TripCancelledEvent event =
        TripCancelledEvent.builder()
            .tripId(tripId)
            .tripCode("TRP-123")
            .customerId(UUID.randomUUID())
            .driverId(UUID.randomUUID())
            .cancellationFee(new BigDecimal("10000.00"))
            .paymentMethod(PaymentMethod.CASH)
            .cancelledAt(Instant.now())
            .build();

    EventEnvelope<TripCancelledEvent> envelope =
        EventEnvelope.of("TripCancelled", "trip-service", "corr-123", event);
    String payload = objectMapper.writeValueAsString(envelope);

    OutboxEvent outboxEvent =
        OutboxEvent.builder()
            .id(UUID.randomUUID())
            .aggregateType("TRIP")
            .aggregateId(tripId.toString())
            .eventType("TripCancelled")
            .payload(payload)
            .status("PENDING")
            .retryCount(0)
            .build();

    when(outboxEventRepository.findPendingEventsForUpdate(50)).thenReturn(List.of(outboxEvent));
    when(kafkaTemplate.send(eq(TripEventProducer.TOPIC_TRIP_CANCELLED), eq(tripId.toString()), any()))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    publisherJob.publishPendingOutboxEvents();

    verify(kafkaTemplate, times(1))
        .send(eq(TripEventProducer.TOPIC_TRIP_CANCELLED), eq(tripId.toString()), any());
    assertEquals("PROCESSED", outboxEvent.getStatus());
    verify(outboxEventRepository, times(1)).save(outboxEvent);
  }
}
