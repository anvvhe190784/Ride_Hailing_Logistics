package com.ridehailing.logistics.common.event;

import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {
  @Builder.Default private String eventId = UUID.randomUUID().toString();
  private String eventType;
  @Builder.Default private int eventVersion = 1;
  @Builder.Default private Instant occurredAt = Instant.now();
  private String correlationId;
  private String producer;
  private T payload;

  public static <T> EventEnvelope<T> of(
      String eventType, String producer, String correlationId, T payload) {
    return EventEnvelope.<T>builder()
        .eventType(eventType)
        .producer(producer)
        .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
        .payload(payload)
        .build();
  }
}
