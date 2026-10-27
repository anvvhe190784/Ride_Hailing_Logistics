package com.ridehailing.logistics.payment.event;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.payment.service.PaymentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripCompletedConsumerTest {

  @Mock private PaymentService paymentService;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private TripCompletedConsumer consumer;

  private UUID tripId;
  private UUID customerId;
  private UUID driverId;
  private TripCompletedEvent event;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    driverId = UUID.randomUUID();

    event =
        TripCompletedEvent.builder()
            .tripId(tripId)
            .customerId(customerId)
            .driverId(driverId)
            .finalFare(new BigDecimal("75000.00"))
            .paymentMethod(PaymentMethod.CASH)
            .completedAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("should consume trip completed event and process payment")
  void shouldConsumeTripCompletedEvent() {
    EventEnvelope<TripCompletedEvent> envelope =
        EventEnvelope.of("trip.completed", "trip-service", "corr-test-123", event);

    consumer.consumeTripCompleted(envelope);

    verify(paymentService, times(1))
        .processTripPayment(
            eq(tripId),
            eq(customerId),
            eq(driverId),
            eq(new BigDecimal("75000.00")),
            eq(PaymentMethod.CASH),
            eq("PAY-TRIP-" + tripId));
  }
}
