package com.ridehailing.logistics.trip.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.lock.RedisDistributedLockService;
import com.ridehailing.logistics.trip.client.DriverClient;
import com.ridehailing.logistics.trip.client.LocationClient;
import com.ridehailing.logistics.trip.client.PricingClient;
import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import com.ridehailing.logistics.trip.domain.entity.OutboxEvent;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.dto.CreateTripRequest;
import com.ridehailing.logistics.trip.dto.NearbyDriverResponse;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import com.ridehailing.logistics.trip.dto.response.CancellationFeeResponse;
import com.ridehailing.logistics.trip.event.TripCompletedInternalEvent;
import com.ridehailing.logistics.trip.event.TripEventProducer;
import com.ridehailing.logistics.trip.repository.DriverOfferRepository;
import com.ridehailing.logistics.trip.repository.OutboxEventRepository;
import com.ridehailing.logistics.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceImplTest {

  @Mock private TripRepository tripRepository;
  @Mock private DriverOfferRepository driverOfferRepository;
  @Mock private PricingClient pricingClient;
  @Mock private LocationClient locationClient;
  @Mock private DriverClient driverClient;
  @Mock private TripEventProducer eventProducer;
  @Mock private OutboxEventRepository outboxEventRepository;
  @Mock private ApplicationEventPublisher applicationEventPublisher;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private RedisDistributedLockService lockService;
  @Spy private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @InjectMocks private TripServiceImpl tripService;

  private UUID customerId;
  private UUID quoteId;
  private String idempotencyKey;

  @BeforeEach
  void setUp() {
    customerId = UUID.randomUUID();
    quoteId = UUID.randomUUID();
    idempotencyKey = "key-" + UUID.randomUUID();

    lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    lenient().when(lockService.tryAcquire(anyString(), anyString(), any())).thenReturn(true);
  }

  @Test
  @DisplayName("createTrip creates trip, queries nearby drivers with pickup coords, and batch saves offers")
  void shouldCreateTripAndBatchSaveOffers() {
    CreateTripRequest request =
        new CreateTripRequest(quoteId, idempotencyKey, PaymentMethod.CASH);

    FareQuoteResponse quoteResponse =
        new FareQuoteResponse(
            quoteId,
            customerId,
            ServiceType.RIDE,
            "123 Hoan Kiem, Ha Noi",
            "456 Cau Giay, Ha Noi",
            5000,
            600,
            new BigDecimal("15000"),
            new BigDecimal("35000"),
            BigDecimal.ZERO,
            BigDecimal.ONE,
            new BigDecimal("50000"),
            "VND",
            1,
            Instant.now().plusSeconds(600),
            21.0300,
            105.8500,
            21.0400,
            105.7800);

    when(tripRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
    when(tripRepository.existsByQuoteId(quoteId)).thenReturn(false);
    when(pricingClient.consumeQuote(quoteId, customerId))
        .thenReturn(ApiResponse.success(quoteResponse, "corr-1"));

    when(tripRepository.save(any(Trip.class)))
        .thenAnswer(
            invocation -> {
              Trip t = invocation.getArgument(0);
              t.setId(UUID.randomUUID());
              return t;
            });

    UUID driverId1 = UUID.randomUUID();
    UUID driverId2 = UUID.randomUUID();
    List<NearbyDriverResponse> nearbyDrivers =
        List.of(
            new NearbyDriverResponse(driverId1, 21.0310, 105.8510, 150.0),
            new NearbyDriverResponse(driverId2, 21.0320, 105.8520, 300.0));

    when(locationClient.getNearbyDrivers(eq(21.0300), eq(105.8500), eq(3000.0), eq(5)))
        .thenReturn(ApiResponse.success(nearbyDrivers, "corr-2"));

    TripDetailResponse result = tripService.createTrip(customerId, request);

    assertNotNull(result);
    assertEquals(TripStatus.MATCHING, result.status());

    // Verify batch saveAll was called for offers
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DriverOffer>> offersCaptor = ArgumentCaptor.forClass(List.class);
    verify(driverOfferRepository, times(1)).saveAll(offersCaptor.capture());
    List<DriverOffer> savedOffers = offersCaptor.getValue();
    assertEquals(2, savedOffers.size());
    assertEquals(driverId1, savedOffers.get(0).getDriverId());
    assertEquals(driverId2, savedOffers.get(1).getDriverId());
    assertEquals("PENDING", savedOffers.get(0).getStatus());
  }

  @Test
  @DisplayName("respondOffer should catch DataIntegrityViolationException and throw BaseDomainException conflict TRIP_ALREADY_CLAIMED")
  void shouldThrowConflictWhenClaimEncountersDataIntegrityViolation() {
    UUID offerId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    UUID tripId = UUID.randomUUID();

    DriverOffer offer =
        DriverOffer.builder()
            .id(offerId)
            .tripId(tripId)
            .driverId(driverId)
            .status("PENDING")
            .expiresAt(Instant.now().plusSeconds(30))
            .build();

    when(driverOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
    when(tripRepository.existsByDriverIdAndStatusIn(eq(driverId), anyList())).thenReturn(false);
    when(tripRepository.claimTripAtomically(eq(tripId), eq(driverId), any(Instant.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint idx_trips_driver_active_unique"));

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> tripService.respondOffer(offerId, driverId, true));

    assertEquals("TRIP_ALREADY_CLAIMED", ex.getErrorCode());
    assertEquals("Chuyến đi đã được tài xế khác tiếp nhận", ex.getMessage());
  }

  @Test
  @DisplayName("updateTripStatus to COMPLETED should publish TripCompletedInternalEvent and release driver")
  void shouldPublishInternalEventWhenTripCompleted() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-112233")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.IN_TRIP)
            .serviceType(ServiceType.RIDE)
            .finalFare(new BigDecimal("50000"))
            .currency("VND")
            .paymentMethod(PaymentMethod.CASH)
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response = tripService.updateTripStatus(tripId, driverId, TripStatus.COMPLETED);

    assertNotNull(response);
    assertEquals(TripStatus.COMPLETED, response.status());

    // Verify Transactional Outbox Event was recorded
    verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    // Verify ApplicationEventPublisher published TripCompletedInternalEvent
    verify(applicationEventPublisher, times(1)).publishEvent(any(TripCompletedInternalEvent.class));
    // Verify driver availability updated to AVAILABLE outside tx
    verify(driverClient, times(1)).updateDriverAvailability(driverId, "DRIVER", DriverAvailabilityStatus.AVAILABLE);
  }

  @Test
  @DisplayName("ADMIN emergency cancellation should succeed and expire pending offers for MATCHING trip")
  void shouldAllowAdminEmergencyCancellationAndExpireOffers() {
    UUID tripId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-MATCHING-1")
            .customerId(customerId)
            .driverId(null)
            .status(TripStatus.MATCHING)
            .serviceType(ServiceType.RIDE)
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response =
        tripService.updateTripStatus(tripId, adminId, "ADMIN", TripStatus.CANCELLED);

    assertNotNull(response);
    assertEquals(TripStatus.CANCELLED, response.status());
    assertEquals(TripStatus.CANCELLED, trip.getStatus());
    assertNotNull(trip.getCancelledAt());
    verify(driverOfferRepository, times(1))
        .expirePendingOffersByTripIds(eq(List.of(tripId)), any(Instant.class));
  }

  @Test
  @DisplayName("Transition to ARRIVED should set arrivedAt")
  void shouldSetArrivedAtOnArrivedStatus() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-ARRIVED-1")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.PICKING_UP)
            .serviceType(ServiceType.RIDE)
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response =
        tripService.updateTripStatus(tripId, driverId, "DRIVER", TripStatus.ARRIVED);

    assertNotNull(response);
    assertEquals(TripStatus.ARRIVED, response.status());
    assertNotNull(trip.getArrivedAt());
  }

  @Test
  @DisplayName("Transition to IN_TRIP should set inTripAt and startedAt")
  void shouldSetInTripAtAndStartedAtOnInTripStatus() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-INTRIP-1")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.ARRIVED)
            .serviceType(ServiceType.RIDE)
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response =
        tripService.updateTripStatus(tripId, driverId, "DRIVER", TripStatus.IN_TRIP);

    assertNotNull(response);
    assertEquals(TripStatus.IN_TRIP, response.status());
    assertNotNull(trip.getInTripAt());
    assertNotNull(trip.getStartedAt());
  }

  @Test
  @DisplayName("respondOffer should reject with 409 TRIP_ALREADY_ASSIGNED when claim lock acquisition fails")
  void shouldRejectOfferWhenRedisClaimLockFails() {
    UUID offerId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    UUID tripId = UUID.randomUUID();

    DriverOffer offer =
        DriverOffer.builder()
            .id(offerId)
            .tripId(tripId)
            .driverId(driverId)
            .status("PENDING")
            .expiresAt(Instant.now().plusSeconds(30))
            .build();

    when(driverOfferRepository.findById(offerId)).thenReturn(Optional.of(offer));
    when(lockService.tryAcquire(
            eq(TripServiceImpl.REDIS_TRIP_CLAIM_LOCK_PREFIX + tripId), anyString(), any()))
        .thenReturn(false);

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> tripService.respondOffer(offerId, driverId, true));

    assertEquals("TRIP_ALREADY_ASSIGNED", ex.getErrorCode());
    assertEquals(
        "Trip is no longer available or is currently being claimed by another driver",
        ex.getMessage());
    assertEquals("REJECTED", offer.getStatus());
    assertNotNull(offer.getRespondedAt());
    verify(driverOfferRepository, times(1)).save(offer);
    verify(tripRepository, never()).claimTripAtomically(any(), any(), any());
  }

  @Test
  @DisplayName("createTrip should return cached response from Redis idempotency if present")
  void shouldReturnCachedTripFromRedisIdempotency() throws Exception {
    CreateTripRequest request =
        new CreateTripRequest(quoteId, idempotencyKey, PaymentMethod.CASH);

    TripDetailResponse cachedResponse =
        new TripDetailResponse(
            UUID.randomUUID(),
            "TRP-CACHED-1",
            ServiceType.RIDE,
            customerId,
            null,
            TripStatus.MATCHING,
            quoteId,
            new BigDecimal("50000"),
            "VND",
            PaymentMethod.CASH,
            Instant.now(),
            null);

    String cachedJson = objectMapper.writeValueAsString(cachedResponse);
    when(valueOperations.get(TripServiceImpl.REDIS_TRIP_IDEMPOTENCY_PREFIX + idempotencyKey))
        .thenReturn(cachedJson);

    TripDetailResponse result = tripService.createTrip(customerId, request);

    assertNotNull(result);
    assertEquals("TRP-CACHED-1", result.tripCode());
    verify(pricingClient, never()).consumeQuote(any(), any());
    verify(tripRepository, never()).save(any());
  }

  @Test
  @DisplayName("createTrip should throw conflict IDEMPOTENCY_IN_PROGRESS if idempotency lock fails")
  void shouldThrowConflictWhenIdempotencyLockFails() {
    CreateTripRequest request =
        new CreateTripRequest(quoteId, idempotencyKey, PaymentMethod.CASH);

    when(valueOperations.get(TripServiceImpl.REDIS_TRIP_IDEMPOTENCY_PREFIX + idempotencyKey))
        .thenReturn(null);
    when(lockService.tryAcquire(
            eq(TripServiceImpl.REDIS_TRIP_IDEMPOTENCY_LOCK_PREFIX + idempotencyKey),
            anyString(),
            any()))
        .thenReturn(false);

    BaseDomainException ex =
        assertThrows(
            BaseDomainException.class,
            () -> tripService.createTrip(customerId, request));

    assertEquals("IDEMPOTENCY_IN_PROGRESS", ex.getErrorCode());
    verify(pricingClient, never()).consumeQuote(any(), any());
  }

  @Test
  @DisplayName("FR-CAN-003: Customer cancels within 3-minute grace period should incur 0 VND cancellation fee")
  void shouldChargeZeroFeeWhenCustomerCancelsDuringGracePeriod() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-GRACE-1")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.PICKING_UP)
            .serviceType(ServiceType.RIDE)
            .acceptedAt(Instant.now().minusSeconds(60)) // 1 minute ago (< 3 min)
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response =
        tripService.updateTripStatus(tripId, customerId, "CUSTOMER", TripStatus.CANCELLED);

    assertNotNull(response);
    assertEquals(TripStatus.CANCELLED, response.status());
    assertEquals(BigDecimal.ZERO, trip.getCancellationFee());
  }

  @Test
  @DisplayName("FR-CAN-003: Customer cancels after 3 minutes in PICKING_UP should incur 10,000 VND fee")
  void shouldChargePenaltyFeeWhenCustomerCancelsAfterGracePeriod() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-PENALTY-1")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.PICKING_UP)
            .serviceType(ServiceType.RIDE)
            .acceptedAt(Instant.now().minusSeconds(240)) // 4 minutes ago (> 3 min)
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response =
        tripService.updateTripStatus(tripId, customerId, "CUSTOMER", TripStatus.CANCELLED);

    assertNotNull(response);
    assertEquals(TripStatus.CANCELLED, response.status());
    assertEquals(new BigDecimal("10000.00"), trip.getCancellationFee());
  }

  @Test
  @DisplayName("FR-CAN-003: Customer cancels when driver ARRIVED should incur 15,000 VND fee")
  void shouldChargePenaltyFeeWhenCustomerCancelsWhenDriverArrived() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-ARRIVED-CANCEL")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.ARRIVED)
            .serviceType(ServiceType.RIDE)
            .acceptedAt(Instant.now().minusSeconds(300))
            .arrivedAt(Instant.now().minusSeconds(60))
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));
    when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

    TripDetailResponse response =
        tripService.updateTripStatus(tripId, customerId, "CUSTOMER", TripStatus.CANCELLED);

    assertNotNull(response);
    assertEquals(TripStatus.CANCELLED, response.status());
    assertEquals(new BigDecimal("15000.00"), trip.getCancellationFee());
  }

  @Test
  @DisplayName("FR-CAN-004: estimateCancellationFee should provide accurate preview before cancellation")
  void shouldEstimateCancellationFeeProperly() {
    UUID tripId = UUID.randomUUID();
    UUID driverId = UUID.randomUUID();
    Trip trip =
        Trip.builder()
            .id(tripId)
            .tripCode("TRP-PREVIEW-1")
            .customerId(customerId)
            .driverId(driverId)
            .status(TripStatus.ARRIVED)
            .serviceType(ServiceType.RIDE)
            .acceptedAt(Instant.now().minusSeconds(300))
            .arrivedAt(Instant.now().minusSeconds(60))
            .build();

    when(tripRepository.findById(tripId)).thenReturn(Optional.of(trip));

    CancellationFeeResponse preview =
        tripService.estimateCancellationFee(tripId, customerId, "CUSTOMER");

    assertNotNull(preview);
    assertEquals(tripId, preview.tripId());
    assertEquals(new BigDecimal("15000.00"), preview.cancellationFee());
    assertFalse(preview.gracePeriodActive());
  }
}
