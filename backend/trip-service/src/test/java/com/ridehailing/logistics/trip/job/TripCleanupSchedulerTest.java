package com.ridehailing.logistics.trip.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.client.DriverClient;
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
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class TripCleanupSchedulerTest {

  @Mock private TripRepository tripRepository;
  @Mock private DriverOfferRepository driverOfferRepository;
  @Mock private TripStatusHistoryRepository tripStatusHistoryRepository;
  @Mock private DriverClient driverClient;
  @Mock private TransactionTemplate transactionTemplate;

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

    lenient()
        .when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocation -> {
              TransactionCallback<?> callback = invocation.getArgument(0);
              return callback.doInTransaction(mock(TransactionStatus.class));
            });
  }

  @Test
  @DisplayName("cleanStaleMatchingTrips does nothing when no stale matching trips exist")
  void shouldDoNothingWhenNoStaleTrips() {
    when(tripRepository.findByStatusAndCreatedAtBefore(eq(TripStatus.MATCHING), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    scheduler.cleanStaleMatchingTrips();

    verify(driverOfferRepository, times(1)).expireOverdueOffers(any(Instant.class));
    verify(tripRepository, never()).save(any(Trip.class));
    verify(tripStatusHistoryRepository, never()).save(any(TripStatusHistory.class));
  }

  @Test
  @DisplayName("cleanStaleMatchingTrips cancels matching trips, records history, and expires pending offers")
  void shouldCancelStaleMatchingTripsAndExpireOffers() {
    when(tripRepository.findByStatusAndCreatedAtBefore(eq(TripStatus.MATCHING), any(Instant.class)))
        .thenReturn(List.of(staleTrip));

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

    // Verify batch offer expiration methods called
    verify(driverOfferRepository, times(1))
        .expirePendingOffersByTripIds(eq(List.of(tripId)), any(Instant.class));
    verify(driverOfferRepository, times(1)).expireOverdueOffers(any(Instant.class));
  }

  @Test
  @DisplayName("cleanDriverNoShowTrips does nothing when no stale accepted/picking up/arrived trips exist")
  void shouldDoNothingWhenNoDriverNoShowTrips() {
    when(tripRepository.findByStatusInAndAcceptedAtBefore(any(), any(Instant.class)))
        .thenReturn(Collections.emptyList());

    scheduler.cleanDriverNoShowTrips();

    verify(tripRepository, never()).save(any(Trip.class));
    verify(tripStatusHistoryRepository, never()).save(any(TripStatusHistory.class));
    verify(driverClient, never()).updateDriverAvailability(any(), any(), any());
  }

  @Test
  @DisplayName("cleanDriverNoShowTrips cancels trips, records history, and resets driver availability")
  void shouldCancelDriverNoShowTripsAndResetDriver() {
    UUID driverId = UUID.randomUUID();
    Trip acceptedTrip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-TEST02")
            .status(TripStatus.ACCEPTED)
            .driverId(driverId)
            .customerId(UUID.randomUUID())
            .acceptedAt(Instant.now().minus(35, ChronoUnit.MINUTES))
            .build();

    when(tripRepository.findByStatusInAndAcceptedAtBefore(
            eq(List.of(TripStatus.ACCEPTED, TripStatus.PICKING_UP, TripStatus.ARRIVED)),
            any(Instant.class)))
        .thenReturn(List.of(acceptedTrip));

    scheduler.cleanDriverNoShowTrips();

    assertEquals(TripStatus.CANCELLED, acceptedTrip.getStatus());
    assertEquals("DRIVER_NO_SHOW", acceptedTrip.getCancellationReason());
    assertNotNull(acceptedTrip.getCancelledAt());
    verify(tripRepository, times(1)).save(acceptedTrip);

    ArgumentCaptor<TripStatusHistory> historyCaptor =
        ArgumentCaptor.forClass(TripStatusHistory.class);
    verify(tripStatusHistoryRepository, times(1)).save(historyCaptor.capture());
    TripStatusHistory savedHistory = historyCaptor.getValue();
    assertEquals(tripId, savedHistory.getTripId());
    assertEquals("ACCEPTED", savedHistory.getFromStatus());
    assertEquals("CANCELLED", savedHistory.getToStatus());
    assertEquals("SYSTEM", savedHistory.getActorRole());
    assertEquals("DRIVER_NO_SHOW", savedHistory.getReason());

    verify(driverClient, times(1))
        .updateDriverAvailability(driverId, "DRIVER", DriverAvailabilityStatus.AVAILABLE);
  }

  @Test
  @DisplayName("cleanStaleInTripTrips cancels trips stuck in IN_TRIP > 6h and resets driver availability")
  void shouldCancelStaleInTripTripsAndResetDriver() {
    UUID driverId = UUID.randomUUID();
    Trip inTrip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-TEST03")
            .status(TripStatus.IN_TRIP)
            .driverId(driverId)
            .customerId(UUID.randomUUID())
            .inTripAt(Instant.now().minus(7, ChronoUnit.HOURS))
            .build();

    when(tripRepository.findStaleInTripTrips(eq(TripStatus.IN_TRIP), any(Instant.class)))
        .thenReturn(List.of(inTrip));

    scheduler.cleanStaleInTripTrips();

    assertEquals(TripStatus.CANCELLED, inTrip.getStatus());
    assertEquals("STALE_IN_TRIP_TIMEOUT", inTrip.getCancellationReason());
    assertNotNull(inTrip.getCancelledAt());
    verify(tripRepository, times(1)).save(inTrip);

    ArgumentCaptor<TripStatusHistory> historyCaptor =
        ArgumentCaptor.forClass(TripStatusHistory.class);
    verify(tripStatusHistoryRepository, times(1)).save(historyCaptor.capture());
    TripStatusHistory savedHistory = historyCaptor.getValue();
    assertEquals(tripId, savedHistory.getTripId());
    assertEquals("IN_TRIP", savedHistory.getFromStatus());
    assertEquals("CANCELLED", savedHistory.getToStatus());
    assertEquals("SYSTEM", savedHistory.getActorRole());
    assertEquals("STALE_IN_TRIP_TIMEOUT", savedHistory.getReason());

    verify(driverClient, times(1))
        .updateDriverAvailability(driverId, "DRIVER", DriverAvailabilityStatus.AVAILABLE);
  }
}
