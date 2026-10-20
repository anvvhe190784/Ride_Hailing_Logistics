package com.ridehailing.logistics.trip.job;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.domain.entity.TripStatusHistory;
import com.ridehailing.logistics.trip.repository.DriverOfferRepository;
import com.ridehailing.logistics.trip.repository.TripRepository;
import com.ridehailing.logistics.trip.repository.TripStatusHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripCleanupScheduler {

  private static final String CANCEL_REASON = "EXPIRED_NO_DRIVER_FOUND";
  private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);

  private final TripRepository tripRepository;
  private final DriverOfferRepository driverOfferRepository;
  private final TripStatusHistoryRepository tripStatusHistoryRepository;

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void cleanStaleMatchingTrips() {
    Instant threshold = Instant.now().minus(STALE_THRESHOLD);
    List<Trip> staleTrips =
        tripRepository.findByStatusAndCreatedAtBefore(TripStatus.MATCHING, threshold);

    if (staleTrips.isEmpty()) {
      return;
    }

    log.info("Found {} stale matching trip(s) to expire", staleTrips.size());
    Instant now = Instant.now();

    for (Trip trip : staleTrips) {
      trip.setStatus(TripStatus.CANCELLED);
      trip.setCancellationReason(CANCEL_REASON);
      trip.setCancelledAt(now);

      TripStatusHistory history =
          TripStatusHistory.builder()
              .tripId(trip.getId())
              .fromStatus(TripStatus.MATCHING.name())
              .toStatus(TripStatus.CANCELLED.name())
              .actorId(null)
              .actorRole("SYSTEM")
              .reason(CANCEL_REASON)
              .createdAt(now)
              .build();
      tripStatusHistoryRepository.save(history);

      List<DriverOffer> offers = driverOfferRepository.findByTripId(trip.getId());
      boolean hasUpdatedOffers = false;
      for (DriverOffer offer : offers) {
        if ("PENDING".equals(offer.getStatus())) {
          offer.setStatus("EXPIRED");
          hasUpdatedOffers = true;
        }
      }
      if (hasUpdatedOffers) {
        driverOfferRepository.saveAll(offers);
      }

      tripRepository.save(trip);
      log.info(
          "Auto-cancelled stale matching trip ID {} (code: {})", trip.getId(), trip.getTripCode());
    }
  }
}
