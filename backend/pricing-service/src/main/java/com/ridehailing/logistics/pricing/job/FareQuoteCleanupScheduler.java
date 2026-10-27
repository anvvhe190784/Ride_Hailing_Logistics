package com.ridehailing.logistics.pricing.job;

import com.ridehailing.logistics.pricing.repository.FareQuoteRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FareQuoteCleanupScheduler {

  private final FareQuoteRepository fareQuoteRepository;

  @Scheduled(fixedDelay = 300000)
  @SchedulerLock(name = "cleanExpiredUnusedQuotes", lockAtLeastFor = "10s", lockAtMostFor = "10m")
  @Transactional
  public void cleanExpiredUnusedQuotes() {
    Instant now = Instant.now();
    int deleted = fareQuoteRepository.deleteExpiredUnusedQuotes(now);
    if (deleted > 0) {
      log.info("Cleaned up {} expired unused fare quote(s) prior to {}", deleted, now);
    }
  }
}
