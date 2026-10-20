package com.ridehailing.logistics.trip.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.domain.entity.TripStatusHistory;
import com.ridehailing.logistics.trip.repository.DriverOfferRepository;
import com.ridehailing.logistics.trip.repository.TripRepository;
import com.ridehailing.logistics.trip.repository.TripStatusHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripCleanupSchedulerTest {

  @Mock private TripRepository tripRepository;
  @Mock private DriverOfferRepository driverOfferRepository;
  @Mock private TripStatusHistoryRepository tripStatusHistoryRepository;

  @InjectMocks private TripCleanupScheduler scheduler;

  private UUID tripId;
  private Trip staleTrip;

  @BeforeEach
  void setUp() {
    tripId = UUID.randomUUID();
    staleTrip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-TEST01")
            .status(TripStatus.MATCHING)
            .customerId(UUID.randomUUID())
            .createdAt(Instant.now().minus(10, ChronoUnit.MINUTES))
            .build();
  }

  @Test
  @DisplayName("cleanStaleMatchingTrips does nothing when no stale matching trips exist")
  void shouldDoNothingWhenNoStaleTrips() {
    when(tripRepository.findByStatusAndCreatedAtBefore(eq(TripStatus.MATCHING), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    scheduler.cleanStaleMatchingTrips();

    verify(tripRepository, never()).save(any(Trip.class));
    verify(tripStatusHistoryRepository, never()).save(any(TripStatusHistory.class));
    verify(driverOfferRepository, never()).saveAll(any());
  }

  @Test
  @DisplayName("cleanStaleMatchingTrips cancels matching trips, records history, and expires pending offers")
  void shouldCancelStaleMatchingTripsAndExpireOffers() {
    when(tripRepository.findByStatusAndCreatedAtBefore(eq(TripStatus.MATCHING), any(Instant.class)))
        .thenReturn(List.of(staleTrip));

    DriverOffer pendingOffer =
        DriverOffer.builder()
            .id(UUID.randomUUID())
            .tripId(tripId)
            .driverId(UUID.randomUUID())
            .status("PENDING")
            .build();

    DriverOffer rejectedOffer =
        DriverOffer.builder()
            .id(UUID.randomUUID())
            .tripId(tripId)
            .driverId(UUID.randomUUID())
            .status("REJECTED")
            .build();

    when(driverOfferRepository.findByTripId(tripId))
        .thenReturn(List.of(pendingOffer, rejectedOffer));

    scheduler.cleanStaleMatchingTrips();

    // Verify trip status updated
    assertEquals(TripStatus.CANCELLED, staleTrip.getStatus());
    assertEquals("EXPIRED_NO_DRIVER_FOUND", staleTrip.getCancellationReason());
    assertNotNull(staleTrip.getCancelledAt());
    verify(tripRepository, times(1)).save(staleTrip);

    // Verify history saved
    ArgumentCaptor<TripStatusHistory> historyCaptor =
        ArgumentCaptor.forClass(TripStatusHistory.class);
    verify(tripStatusHistoryRepository, times(1)).save(historyCaptor.capture());
    TripStatusHistory savedHistory = historyCaptor.getValue();
    assertEquals(tripId, savedHistory.getTripId());
    assertEquals("MATCHING", savedHistory.getFromStatus());
    assertEquals("CANCELLED", savedHistory.getToStatus());
    assertEquals("SYSTEM", savedHistory.getActorRole());
    assertEquals("EXPIRED_NO_DRIVER_FOUND", savedHistory.getReason());

    // Verify offer status updated
    assertEquals("EXPIRED", pendingOffer.getStatus());
    assertEquals("REJECTED", rejectedOffer.getStatus());
    verify(driverOfferRepository, times(1)).saveAll(any());
  }
}
