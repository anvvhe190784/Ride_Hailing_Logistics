package com.ridehailing.logistics.trip.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.common.event.EventEnvelope;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.common.lock.RedisDistributedLockService;
import com.ridehailing.logistics.trip.client.DriverClient;
import com.ridehailing.logistics.trip.client.LocationClient;
import com.ridehailing.logistics.trip.client.PricingClient;
import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import com.ridehailing.logistics.trip.domain.entity.OutboxEvent;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.dto.CreateTripRequest;
import com.ridehailing.logistics.trip.dto.DriverOfferResponse;
import com.ridehailing.logistics.trip.dto.NearbyDriverResponse;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import com.ridehailing.logistics.trip.event.TripCompletedInternalEvent;
import com.ridehailing.logistics.trip.event.TripEventProducer;
import com.ridehailing.logistics.trip.repository.DriverOfferRepository;
import com.ridehailing.logistics.trip.repository.OutboxEventRepository;
import com.ridehailing.logistics.trip.repository.TripRepository;
import com.ridehailing.logistics.trip.service.TripService;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

  public static final String REDIS_TRIP_IDEMPOTENCY_PREFIX = "idempotency:trip:create:";
  public static final String REDIS_TRIP_IDEMPOTENCY_LOCK_PREFIX = "lock:trip:idempotency:";
  public static final String REDIS_TRIP_CLAIM_LOCK_PREFIX = "lock:trip:claim:";

  private static final String STATUS_REJECTED = "REJECTED";
  private static final String ERR_TRIP_NOT_FOUND = "TRIP_NOT_FOUND";
  private static final String MSG_TRIP_NOT_FOUND = "Trip not found";

  private final TripRepository tripRepository;
  private final DriverOfferRepository driverOfferRepository;
  private final PricingClient pricingClient;
  private final LocationClient locationClient;
  private final DriverClient driverClient;
  private final TripEventProducer eventProducer;
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final StringRedisTemplate redisTemplate;
  private final RedisDistributedLockService lockService;

  @Autowired @Lazy private TripService self;

  @PostConstruct
  void init() {
    if (objectMapper != null) {
      objectMapper.findAndRegisterModules();
    }
  }

  private TripService getSelf() {
    return self != null ? self : this;
  }

  @Override
  public TripDetailResponse createTrip(UUID customerId, CreateTripRequest request) {
    if (request.idempotencyKey() != null
        && !request.idempotencyKey().isBlank()
        && redisTemplate != null) {
      try {
        String cachedJson =
            redisTemplate
                .opsForValue()
                .get(REDIS_TRIP_IDEMPOTENCY_PREFIX + request.idempotencyKey());
        if (cachedJson != null) {
          log.info("Redis idempotency cache hit for key {}", request.idempotencyKey());
          return objectMapper.readValue(cachedJson, TripDetailResponse.class);
        }
      } catch (Exception e) {
        log.warn(
            "Failed to read/deserialize cached trip for idempotency key {}: {}",
            request.idempotencyKey(),
            e.getMessage());
      }
    }

    boolean hasIdempKey = request.idempotencyKey() != null && !request.idempotencyKey().isBlank();
    String idempLockKey =
        hasIdempKey ? REDIS_TRIP_IDEMPOTENCY_LOCK_PREFIX + request.idempotencyKey() : null;
    String idempOwner = hasIdempKey ? customerId + ":" + UUID.randomUUID() : null;

    if (hasIdempKey && lockService != null) {
      if (!lockService.tryAcquire(idempLockKey, idempOwner, Duration.ofSeconds(10))) {
        throw BaseDomainException.conflict(
            "IDEMPOTENCY_IN_PROGRESS",
            "A trip creation request with this idempotency key is already in progress");
      }
    }

    try {
      // FR-TRIP-002: Idempotency Key check
      Optional<Trip> existingTrip = tripRepository.findByIdempotencyKey(request.idempotencyKey());
      if (existingTrip.isPresent()) {
        log.info(
            "Idempotent request detected for key {}. Returning existing trip.",
            request.idempotencyKey());
        TripDetailResponse existingResponse = toResponse(existingTrip.get());
        cacheTripIdempotencyResponse(request.idempotencyKey(), existingResponse);
        return existingResponse;
      }

      // Check if quote has already been used for a trip
      if (tripRepository.existsByQuoteId(request.quoteId())) {
        throw BaseDomainException.conflict(
            "QUOTE_ALREADY_USED", "A trip has already been created for this quote");
      }

      // Consume quote atomically via pricing-service (Remote I/O outside @Transactional)
      ApiResponse<FareQuoteResponse> quoteRes =
          pricingClient.consumeQuote(request.quoteId(), customerId);
      if (quoteRes == null || quoteRes.getData() == null) {
        throw BaseDomainException.badRequest(
            "QUOTE_CONSUME_FAILED", "Failed to consume fare quote or quote is invalid/expired");
      }
      FareQuoteResponse quote = quoteRes.getData();

      String quoteSnapshot;
      try {
        quoteSnapshot = objectMapper.writeValueAsString(quote);
      } catch (Exception e) {
        quoteSnapshot = "{}";
      }

      String tripCode = "TRP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

      GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
      Point pickupPoint;
      if (quote.pickupLat() != null && quote.pickupLng() != null) {
        pickupPoint =
            geometryFactory.createPoint(new Coordinate(quote.pickupLng(), quote.pickupLat()));
      } else {
        pickupPoint = geometryFactory.createPoint(new Coordinate(105.8542, 21.0285));
      }

      Trip trip =
          Trip.builder()
              .tripCode(tripCode)
              .serviceType(quote.serviceType())
              .customerId(customerId)
              .quoteId(request.quoteId())
              .idempotencyKey(request.idempotencyKey())
              .quoteSnapshot(quoteSnapshot)
              .finalFare(quote.totalFare())
              .currency(quote.currency())
              .paymentMethod(request.paymentMethod())
              .status(TripStatus.MATCHING) // Direct transition to MATCHING per FR-TRIP-006
              .pickupPoint(pickupPoint)
              .build();

      trip = getSelf().saveInitialTrip(trip);

      // Trigger driver dispatch by finding nearby available drivers (Remote I/O outside
      // @Transactional)
      try {
        double pickupLat = trip.getPickupPoint() != null ? trip.getPickupPoint().getY() : 21.0285;
        double pickupLng = trip.getPickupPoint() != null ? trip.getPickupPoint().getX() : 105.8542;
        ApiResponse<List<NearbyDriverResponse>> driversRes =
            locationClient.getNearbyDrivers(pickupLat, pickupLng, 3000.0, 5);
        if (driversRes != null && driversRes.getData() != null) {
          List<DriverOffer> offers = new ArrayList<>();
          for (NearbyDriverResponse driver : driversRes.getData()) {
            DriverOffer offer =
                DriverOffer.builder()
                    .tripId(trip.getId())
                    .driverId(driver.driverId())
                    .status("PENDING")
                    .estimatedPickupDistanceMeters((int) Math.round(driver.distanceMeters()))
                    .expiresAt(Instant.now().plus(Duration.ofSeconds(15)))
                    .build();
            offers.add(offer);
          }
          if (!offers.isEmpty()) {
            driverOfferRepository.saveAll(offers);
          }
        }
      } catch (Exception e) {
        log.warn("Failed to dispatch driver offers for trip {}: {}", trip.getId(), e.getMessage());
      }

      TripDetailResponse response = toResponse(trip);
      cacheTripIdempotencyResponse(request.idempotencyKey(), response);
      return response;
    } finally {
      if (idempLockKey != null && lockService != null) {
        lockService.release(idempLockKey, idempOwner);
      }
    }
  }

  private void cacheTripIdempotencyResponse(String idempotencyKey, TripDetailResponse response) {
    if (redisTemplate != null && idempotencyKey != null && !idempotencyKey.isBlank()) {
      try {
        String json = objectMapper.writeValueAsString(response);
        redisTemplate
            .opsForValue()
            .set(REDIS_TRIP_IDEMPOTENCY_PREFIX + idempotencyKey, json, Duration.ofMinutes(30));
      } catch (Exception ex) {
        log.warn(
            "Failed to cache trip idempotency response for key {}: {}",
            idempotencyKey,
            ex.getMessage());
      }
    }
  }

  @Override
  @Transactional
  public Trip saveInitialTrip(Trip trip) {
    Point pickupPoint = trip.getPickupPoint();
    Trip saved = tripRepository.save(trip);
    saved.setPickupPoint(pickupPoint);
    log.info(
        "Created trip code {} with ID {} in MATCHING status", trip.getTripCode(), saved.getId());
    return saved;
  }

  @Override
  @Transactional(noRollbackFor = BaseDomainException.class)
  public DriverOfferResponse respondOffer(UUID offerId, UUID driverId, boolean accept) {
    DriverOffer offer =
        driverOfferRepository
            .findById(offerId)
            .orElseThrow(
                () -> BaseDomainException.notFound("OFFER_NOT_FOUND", "Driver offer not found"));

    if (!offer.getDriverId().equals(driverId)) {
      throw BaseDomainException.badRequest("ACCESS_DENIED", "Offer does not belong to this driver");
    }

    if (!"PENDING".equals(offer.getStatus())) {
      throw BaseDomainException.conflict(
          "OFFER_ALREADY_PROCESSED", "Offer is already " + offer.getStatus());
    }

    if (offer.getExpiresAt().isBefore(Instant.now())) {
      offer.setStatus("EXPIRED");
      offer.setRespondedAt(Instant.now());
      driverOfferRepository.save(offer);
      return toOfferResponse(offer);
    }

    if (!accept) {
      offer.setStatus(STATUS_REJECTED);
      offer.setRespondedAt(Instant.now());
      driverOfferRepository.save(offer);
      return toOfferResponse(offer);
    }

    String lockKey = REDIS_TRIP_CLAIM_LOCK_PREFIX + offer.getTripId();
    String ownerToken = driverId + ":" + UUID.randomUUID();
    if (lockService != null && !lockService.tryAcquire(lockKey, ownerToken, Duration.ofSeconds(5))) {
      offer.setStatus(STATUS_REJECTED);
      offer.setRespondedAt(Instant.now());
      driverOfferRepository.save(offer);
      throw BaseDomainException.conflict(
          "TRIP_ALREADY_ASSIGNED",
          "Trip is no longer available or is currently being claimed by another driver");
    }

    try {
      // Check if driver is already on active trip
      boolean hasActiveTrip =
          tripRepository.existsByDriverIdAndStatusIn(
              driverId,
              List.of(
                  TripStatus.ACCEPTED,
                  TripStatus.PICKING_UP,
                  TripStatus.ARRIVED,
                  TripStatus.IN_TRIP));
      if (hasActiveTrip) {
        offer.setStatus(STATUS_REJECTED);
        offer.setRespondedAt(Instant.now());
        driverOfferRepository.save(offer);
        throw BaseDomainException.conflict(
            "DRIVER_ALREADY_ASSIGNED", "Driver is already assigned to an active trip");
      }

      try {
        // Atomic CAS update: claim trip
        int updatedRows =
            tripRepository.claimTripAtomically(offer.getTripId(), driverId, Instant.now());
        if (updatedRows == 0) {
          offer.setStatus(STATUS_REJECTED);
          offer.setRespondedAt(Instant.now());
          driverOfferRepository.save(offer);
          throw BaseDomainException.conflict(
              "TRIP_ALREADY_ASSIGNED",
              "Trip is no longer available or was already accepted by another driver");
        }

        offer.setStatus("ACCEPTED");
        offer.setRespondedAt(Instant.now());
        driverOfferRepository.save(offer);

        log.info(
            "Driver ID {} atomically claimed and accepted Trip ID {}", driverId, offer.getTripId());
        return toOfferResponse(offer);
      } catch (DataIntegrityViolationException ex) {
        log.warn(
            "DataIntegrityViolationException claiming trip {}: {}",
            offer.getTripId(),
            ex.getMessage());
        offer.setStatus(STATUS_REJECTED);
        offer.setRespondedAt(Instant.now());
        try {
          driverOfferRepository.save(offer);
        } catch (Exception ignored) {
          // Ignored if transaction marked rollback
        }
        throw BaseDomainException.conflict(
            "TRIP_ALREADY_CLAIMED", "Chuyến đi đã được tài xế khác tiếp nhận");
      }
    } finally {
      if (lockService != null) {
        lockService.release(lockKey, ownerToken);
      }
    }
  }

  @Override
  public TripDetailResponse updateTripStatus(UUID tripId, UUID actorId, TripStatus nextStatus) {
    return updateTripStatus(tripId, actorId, null, nextStatus);
  }

  @Override
  public TripDetailResponse updateTripStatus(
      UUID tripId, UUID actorId, String actorRole, TripStatus nextStatus) {
    if (nextStatus == TripStatus.ACCEPTED) {
      throw BaseDomainException.badRequest(
          "INVALID_STATE_TRANSITION",
          "Direct transition to ACCEPTED is disallowed; must use respondOffer");
    }

    Trip trip = getSelf().updateTripStatusInDb(tripId, actorId, actorRole, nextStatus);

    if (nextStatus == TripStatus.COMPLETED || nextStatus == TripStatus.CANCELLED) {
      if (trip.getDriverId() != null) {
        try {
          driverClient.updateDriverAvailability(
              trip.getDriverId(), "DRIVER", DriverAvailabilityStatus.AVAILABLE);
        } catch (Exception e) {
          log.warn(
              "Failed to notify driver-service to release driver {} on {}: {}",
              trip.getDriverId(),
              nextStatus,
              e.getMessage());
        }
      }
    }

    return toResponse(trip);
  }

  @Override
  @Transactional
  public Trip updateTripStatusInDb(UUID tripId, UUID actorId, TripStatus nextStatus) {
    return updateTripStatusInDb(tripId, actorId, null, nextStatus);
  }

  @Override
  @Transactional
  public Trip updateTripStatusInDb(
      UUID tripId, UUID actorId, String actorRole, TripStatus nextStatus) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .orElseThrow(
                () -> BaseDomainException.notFound(ERR_TRIP_NOT_FOUND, MSG_TRIP_NOT_FOUND));

    validateStateTransition(trip.getStatus(), nextStatus);

    // Verify actor authorization on the trip
    boolean isAdminOrSupport =
        actorRole != null
            && ("ADMIN".equalsIgnoreCase(actorRole) || "SUPPORT_STAFF".equalsIgnoreCase(actorRole));

    if (nextStatus == TripStatus.CANCELLED) {
      if (!isAdminOrSupport
          && !actorId.equals(trip.getCustomerId())
          && !actorId.equals(trip.getDriverId())) {
        throw BaseDomainException.forbidden(
            "ACCESS_DENIED", "Only trip participants can cancel this trip");
      }
    } else {
      if (!isAdminOrSupport
          && (trip.getDriverId() == null || !actorId.equals(trip.getDriverId()))) {
        throw BaseDomainException.forbidden(
            "ACCESS_DENIED", "Only the assigned driver can update trip progress");
      }
    }

    Instant now = Instant.now();

    // When transitioning from MATCHING to CANCELLED, expire pending driver offers
    if (trip.getStatus() == TripStatus.MATCHING && nextStatus == TripStatus.CANCELLED) {
      driverOfferRepository.expirePendingOffersByTripIds(List.of(tripId), now);
    }

    trip.setStatus(nextStatus);

    if (nextStatus == TripStatus.ARRIVED) {
      trip.setArrivedAt(now);
    } else if (nextStatus == TripStatus.IN_TRIP) {
      trip.setStartedAt(now);
      trip.setInTripAt(now);
    } else if (nextStatus == TripStatus.COMPLETED) {
      trip.setCompletedAt(now);

      // Persist Transactional Outbox Event within the same DB transaction
      saveTripCompletedOutboxEvent(trip);

      // Publish Spring internal event; listener will publish Kafka event AFTER_COMMIT
      applicationEventPublisher.publishEvent(new TripCompletedInternalEvent(trip));
    } else if (nextStatus == TripStatus.CANCELLED) {
      trip.setCancelledAt(now);
      trip.setCancelledBy(actorId);
      if (trip.getCancellationReason() == null) {
        trip.setCancellationReason(
            isAdminOrSupport ? "Cancelled by staff/admin " + actorId : "Cancelled by " + actorId);
      }
    }

    trip = tripRepository.save(trip);
    log.info("Trip ID {} transitioned to status {}", tripId, nextStatus);
    return trip;
  }

  @Override
  @Transactional(readOnly = true)
  public TripDetailResponse getTrip(UUID tripId) {
    Trip trip =
        tripRepository
            .findById(tripId)
            .orElseThrow(
                () -> BaseDomainException.notFound(ERR_TRIP_NOT_FOUND, MSG_TRIP_NOT_FOUND));
    return toResponse(trip);
  }

  private void validateStateTransition(TripStatus current, TripStatus next) {
    boolean valid =
        switch (current) {
          case CREATED -> next == TripStatus.MATCHING || next == TripStatus.CANCELLED;
          case MATCHING -> next == TripStatus.NO_DRIVER || next == TripStatus.CANCELLED;
          case ACCEPTED -> next == TripStatus.PICKING_UP || next == TripStatus.CANCELLED;
          case PICKING_UP -> next == TripStatus.ARRIVED || next == TripStatus.CANCELLED;
          case ARRIVED -> next == TripStatus.IN_TRIP || next == TripStatus.CANCELLED;
          case IN_TRIP -> next == TripStatus.COMPLETED || next == TripStatus.CANCELLED;
          case COMPLETED, CANCELLED, NO_DRIVER -> false;
        };

    if (!valid) {
      throw BaseDomainException.conflict(
          "INVALID_TRIP_STATE",
          String.format("Cannot transition trip from %s to %s", current, next));
    }
  }

  private TripDetailResponse toResponse(Trip t) {
    return new TripDetailResponse(
        t.getId(),
        t.getTripCode(),
        t.getServiceType(),
        t.getCustomerId(),
        t.getDriverId(),
        t.getStatus(),
        t.getQuoteId(),
        t.getFinalFare(),
        t.getCurrency(),
        t.getPaymentMethod(),
        t.getCreatedAt(),
        t.getCompletedAt());
  }

  private DriverOfferResponse toOfferResponse(DriverOffer o) {
    return new DriverOfferResponse(
        o.getId(),
        o.getTripId(),
        o.getDriverId(),
        o.getStatus(),
        o.getEstimatedPickupDistanceMeters(),
        o.getExpiresAt());
  }

  private void saveTripCompletedOutboxEvent(Trip trip) {
    try {
      TripCompletedEvent event =
          TripCompletedEvent.builder()
              .tripId(trip.getId())
              .tripCode(trip.getTripCode())
              .customerId(trip.getCustomerId())
              .driverId(trip.getDriverId())
              .serviceType(trip.getServiceType())
              .finalFare(trip.getFinalFare() != null ? trip.getFinalFare() : BigDecimal.ZERO)
              .currency(trip.getCurrency())
              .paymentMethod(trip.getPaymentMethod())
              .completedAt(trip.getCompletedAt() != null ? trip.getCompletedAt() : Instant.now())
              .build();

      EventEnvelope<TripCompletedEvent> envelope =
          EventEnvelope.of("TripCompleted", "trip-service", UUID.randomUUID().toString(), event);
      String jsonPayload = objectMapper.writeValueAsString(envelope);

      OutboxEvent outboxEvent =
          OutboxEvent.builder()
              .aggregateType("TRIP")
              .aggregateId(trip.getId().toString())
              .eventType("TripCompleted")
              .payload(jsonPayload)
              .status("PENDING")
              .retryCount(0)
              .build();

      outboxEventRepository.save(outboxEvent);
      log.info("Saved OutboxEvent for completed trip ID {}", trip.getId());
    } catch (Exception ex) {
      log.error(
          "Failed to persist OutboxEvent for trip ID {}: {}", trip.getId(), ex.getMessage(), ex);
      throw BaseDomainException.internalError(
          "OUTBOX_PERSIST_ERROR", "Failed to record trip completion outbox event");
    }
  }
}
