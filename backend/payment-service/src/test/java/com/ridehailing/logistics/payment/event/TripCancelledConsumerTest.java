package com.ridehailing.logistics.payment.event;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCancelledEvent;
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
class TripCancelledConsumerTest {

  @Mock private PaymentService paymentService;

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private TripCancelledConsumer consumer;

  private UUID tripId;
  private UUID customerId;
  private UUID driverId;
  private TripCancelledEvent event;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    driverId = UUID.randomUUID();

    event =
        TripCancelledEvent.builder()
            .tripId(tripId)
            .tripCode("TRP-CANCEL-123")
            .customerId(customerId)
            .driverId(driverId)
            .cancellationFee(new BigDecimal("15000.00"))
            .paymentMethod(PaymentMethod.WALLET)
            .cancelledAt(Instant.now())
            .build();
  }

  @Test
  @DisplayName("should consume trip cancelled event and process cancellation penalty")
  void shouldConsumeTripCancelledEvent() {
    EventEnvelope<TripCancelledEvent> envelope =
        EventEnvelope.of("TripCancelled", "trip-service", "corr-test-cancel", event);

    consumer.consumeTripCancelled(envelope);

    verify(paymentService, times(1))
        .processCancellationPenalty(
            tripId,
            customerId,
            driverId,
            new BigDecimal("15000.00"),
            PaymentMethod.WALLET,
            "PENALTY-TRIP-" + tripId);
  }
}
