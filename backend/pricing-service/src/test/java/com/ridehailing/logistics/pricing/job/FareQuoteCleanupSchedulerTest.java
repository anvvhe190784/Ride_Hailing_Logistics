package com.ridehailing.logistics.pricing.job;

import static org.mockito.Mockito.*;

import com.ridehailing.logistics.pricing.repository.FareQuoteRepository;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FareQuoteCleanupSchedulerTest {

  @Mock private FareQuoteRepository fareQuoteRepository;

  @InjectMocks private FareQuoteCleanupScheduler scheduler;

  @Test
  @DisplayName("cleanExpiredUnusedQuotes invokes repository delete with current timestamp")
  void shouldCleanExpiredUnusedQuotes() {
    when(fareQuoteRepository.deleteExpiredUnusedQuotes(any(Instant.class))).thenReturn(5);

    scheduler.cleanExpiredUnusedQuotes();

    verify(fareQuoteRepository, times(1)).deleteExpiredUnusedQuotes(any(Instant.class));
  }
}
