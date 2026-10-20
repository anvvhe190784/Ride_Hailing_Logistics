package com.ridehailing.logistics.payment.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;

class KafkaConsumerConfigTest {

  @Test
  @DisplayName("commonErrorHandler should create a valid CommonErrorHandler with DLT recoverer")
  @SuppressWarnings("unchecked")
  void shouldCreateCommonErrorHandler() {
    KafkaConsumerConfig config = new KafkaConsumerConfig();
    KafkaOperations<Object, Object> kafkaOperations = mock(KafkaOperations.class);

    CommonErrorHandler handler = config.commonErrorHandler(kafkaOperations);

    assertNotNull(handler);
  }
}
