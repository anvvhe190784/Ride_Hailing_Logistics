package com.ridehailing.logistics.trip.job;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.client.DriverClient;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.domain.entity.TripStatusHistory;
import com.ridehailing.logistics.trip.repository.DriverOfferRepository;
import com.ridehailing.logistics.trip.repository.TripRepository;
import com.ridehailing.logistics.trip.repository.TripStatusHistoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class TripCleanupScheduler {

  private static final String CANCEL_REASON = "EXPIRED_NO_DRIVER_FOUND";
  private static final Duration STALE_THRESHOLD = Duration.ofMinutes(5);
  private static final String DRIVER_NO_SHOW_REASON = "DRIVER_NO_SHOW";
  private static final Duration DRIVER_NO_SHOW_THRESHOLD = Duration.ofMinutes(30);
  private static final String STALE_IN_TRIP_REASON = "STALE_IN_TRIP_TIMEOUT";
  private static final Duration STALE_IN_TRIP_THRESHOLD = Duration.ofHours(6);

  private final TripRepository tripRepository;
  private final DriverOfferRepository driverOfferRepository;
  private final TripStatusHistoryRepository tripStatusHistoryRepository;
  private final DriverClient driverClient;
  private final TransactionTemplate transactionTemplate;

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(name = "cleanStaleMatchingTrips", lockAtLeastFor = "10s", lockAtMostFor = "5m")
  @Transactional
  public void cleanStaleMatchingTrips() {
    Instant threshold = Instant.now().minus(STALE_THRESHOLD);
    List<Trip> staleTrips =
        tripRepository.findByStatusAndCreatedAtBefore(TripStatus.MATCHING, threshold);

    Instant now = Instant.now();
    driverOfferRepository.expireOverdueOffers(now);

    if (staleTrips.isEmpty()) {
      return;
    }

    log.info("Found {} stale matching trip(s) to expire", staleTrips.size());
    List<UUID> tripIds = staleTrips.stream().map(Trip::getId).toList();
    driverOfferRepository.expirePendingOffersByTripIds(tripIds, now);

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
      tripRepository.save(trip);
      log.info(
          "Auto-cancelled stale matching trip ID {} (code: {})", trip.getId(), trip.getTripCode());
    }
  }

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(name = "cleanDriverNoShowTrips", lockAtLeastFor = "10s", lockAtMostFor = "5m")
  public void cleanDriverNoShowTrips() {
    Instant threshold = Instant.now().minus(DRIVER_NO_SHOW_THRESHOLD);
    List<Trip> noShowTrips =
        tripRepository.findByStatusInAndAcceptedAtBefore(
            List.of(TripStatus.ACCEPTED, TripStatus.PICKING_UP, TripStatus.ARRIVED), threshold);

    if (noShowTrips.isEmpty()) {
      return;
    }

    log.info("Found {} driver no-show trip(s) to cancel", noShowTrips.size());
    Instant now = Instant.now();

    List<Trip> cancelledTrips =
        transactionTemplate.execute(
            status -> {
              for (Trip trip : noShowTrips) {
                TripStatus previousStatus = trip.getStatus();
                trip.setStatus(TripStatus.CANCELLED);
                trip.setCancellationReason(DRIVER_NO_SHOW_REASON);
                trip.setCancelledAt(now);

                TripStatusHistory history =
                    TripStatusHistory.builder()
                        .tripId(trip.getId())
                        .fromStatus(previousStatus != null ? previousStatus.name() : null)
                        .toStatus(TripStatus.CANCELLED.name())
                        .actorId(null)
                        .actorRole("SYSTEM")
                        .reason(DRIVER_NO_SHOW_REASON)
                        .createdAt(now)
                        .build();
                tripStatusHistoryRepository.save(history);
                tripRepository.save(trip);
              }
              return noShowTrips;
            });

    if (cancelledTrips == null) {
      return;
    }

    for (Trip trip : cancelledTrips) {
      if (trip.getDriverId() != null) {
        try {
          driverClient.updateDriverAvailability(
              trip.getDriverId(), "DRIVER", DriverAvailabilityStatus.AVAILABLE);
        } catch (Exception e) {
          log.error(
              "Failed to release driver {} to AVAILABLE for trip {}",
              trip.getDriverId(),
              trip.getId(),
              e);
        }
      }
      log.info(
          "Auto-cancelled driver no-show trip ID {} (code: {}, driver: {})",
          trip.getId(),
          trip.getTripCode(),
          trip.getDriverId());
    }
  }

  @Scheduled(fixedDelay = 300000)
  @SchedulerLock(name = "cleanStaleInTripTrips", lockAtLeastFor = "10s", lockAtMostFor = "5m")
  public void cleanStaleInTripTrips() {
    Instant threshold = Instant.now().minus(STALE_IN_TRIP_THRESHOLD);
    List<Trip> staleInTripTrips =
        tripRepository.findStaleInTripTrips(TripStatus.IN_TRIP, threshold);

    if (staleInTripTrips.isEmpty()) {
      return;
    }

    log.info("Found {} stale in-trip trip(s) stuck > 6h to cancel", staleInTripTrips.size());
    Instant now = Instant.now();

    List<Trip> cancelledTrips =
        transactionTemplate.execute(
            status -> {
              for (Trip trip : staleInTripTrips) {
                TripStatus previousStatus = trip.getStatus();
                trip.setStatus(TripStatus.CANCELLED);
                trip.setCancellationReason(STALE_IN_TRIP_REASON);
                trip.setCancelledAt(now);

                TripStatusHistory history =
                    TripStatusHistory.builder()
                        .tripId(trip.getId())
                        .fromStatus(
                            previousStatus != null ? previousStatus.name() : TripStatus.IN_TRIP.name())
                        .toStatus(TripStatus.CANCELLED.name())
                        .actorId(null)
                        .actorRole("SYSTEM")
                        .reason(STALE_IN_TRIP_REASON)
                        .createdAt(now)
                        .build();
                tripStatusHistoryRepository.save(history);
                tripRepository.save(trip);
              }
              return staleInTripTrips;
            });

    if (cancelledTrips == null) {
      return;
    }

    for (Trip trip : cancelledTrips) {
      if (trip.getDriverId() != null) {
        try {
          driverClient.updateDriverAvailability(
              trip.getDriverId(), "DRIVER", DriverAvailabilityStatus.AVAILABLE);
        } catch (Exception e) {
          log.error(
              "Failed to release driver {} to AVAILABLE for stale in-trip trip {}",
              trip.getDriverId(),
              trip.getId(),
              e);
        }
      }
      log.info(
          "Auto-cancelled stale in-trip trip ID {} (code: {}, driver: {})",
          trip.getId(),
          trip.getTripCode(),
          trip.getDriverId());
    }
  }
}
