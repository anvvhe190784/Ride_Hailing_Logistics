package com.ridehailing.logistics.payment.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

  @Bean
  public CommonErrorHandler commonErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaOperations,
            (record, ex) -> new TopicPartition("trip.completed.DLT", record.partition()));

    FixedBackOff backOff = new FixedBackOff(2000L, 3L);
    return new DefaultErrorHandler(recoverer, backOff);
  }
}
