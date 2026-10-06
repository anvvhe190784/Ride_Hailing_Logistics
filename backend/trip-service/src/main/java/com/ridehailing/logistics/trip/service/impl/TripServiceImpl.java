package com.ridehailing.logistics.trip.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.common.dto.FareQuoteResponse;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.common.exception.BaseDomainException;
import com.ridehailing.logistics.trip.client.DriverClient;
import com.ridehailing.logistics.trip.client.PricingClient;
import com.ridehailing.logistics.trip.domain.entity.DriverOffer;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.dto.CreateTripRequest;
import com.ridehailing.logistics.trip.dto.DriverOfferResponse;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import com.ridehailing.logistics.trip.event.TripEventProducer;
import com.ridehailing.logistics.trip.lock.RedisDistributedLockService;
import com.ridehailing.logistics.trip.repository.DriverOfferRepository;
import com.ridehailing.logistics.trip.repository.TripRepository;
import com.ridehailing.logistics.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripRepository tripRepository;
    private final DriverOfferRepository driverOfferRepository;
    private final PricingClient pricingClient;
    private final DriverClient driverClient;
    private final RedisDistributedLockService lockService;
    private final TripEventProducer eventProducer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TripDetailResponse createTrip(UUID customerId, CreateTripRequest request) {
        // FR-TRIP-002: Idempotency Key check
        Optional<Trip> existingTrip = tripRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existingTrip.isPresent()) {
            log.info("Idempotent request detected for key {}. Returning existing trip.", request.idempotencyKey());
            return toResponse(existingTrip.get());
        }

        // Fetch quote from pricing service (or fallback)
        ApiResponse<FareQuoteResponse> quoteRes = pricingClient.getQuote(request.quoteId());
        if (quoteRes == null || quoteRes.getData() == null) {
            throw BaseDomainException.notFound("QUOTE_NOT_FOUND", "Specified fare quote not found or expired");
        }
        FareQuoteResponse quote = quoteRes.getData();

        if (quote.expiresAt().isBefore(Instant.now())) {
            throw BaseDomainException.businessRuleViolation("Fare quote has expired. Please request a new quote.");
        }

        String quoteSnapshot;
        try {
            quoteSnapshot = objectMapper.writeValueAsString(quote);
        } catch (Exception e) {
            quoteSnapshot = "{}";
        }

        String tripCode = "TRP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Trip trip = Trip.builder()
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
                .build();

        trip = tripRepository.save(trip);
        log.info("Created trip code {} with ID {} in MATCHING status", tripCode, trip.getId());

        return toResponse(trip);
    }

    @Override
    @Transactional
    public DriverOfferResponse respondOffer(UUID offerId, UUID driverId, boolean accept) {
        DriverOffer offer = driverOfferRepository.findById(offerId)
                .orElseThrow(() -> BaseDomainException.notFound("OFFER_NOT_FOUND", "Driver offer not found"));

        if (!offer.getDriverId().equals(driverId)) {
            throw BaseDomainException.badRequest("ACCESS_DENIED", "Offer does not belong to this driver");
        }

        if (!"PENDING".equals(offer.getStatus())) {
            throw BaseDomainException.conflict("OFFER_ALREADY_PROCESSED", "Offer is already " + offer.getStatus());
        }

        if (offer.getExpiresAt().isBefore(Instant.now())) {
            offer.setStatus("EXPIRED");
            driverOfferRepository.save(offer);
            throw BaseDomainException.conflict("OFFER_EXPIRED", "Offer has expired");
        }

        if (!accept) {
            offer.setStatus("REJECTED");
            offer.setRespondedAt(Instant.now());
            driverOfferRepository.save(offer);
            return toOfferResponse(offer);
        }

        // FR-MAT-010, BR-002: Atomic acceptance with Redis Distributed Lock
        String lockKey = "lock:driver:" + driverId;
        boolean locked = lockService.acquireLock(lockKey, Duration.ofSeconds(5));
        if (!locked) {
            throw BaseDomainException.conflict("CONCURRENT_OPERATION", "Operation is being processed. Please retry.");
        }

        try {
            // Check if driver is already on active trip
            boolean hasActiveTrip = tripRepository.existsByDriverIdAndStatusIn(
                    driverId,
                    List.of(TripStatus.ACCEPTED, TripStatus.PICKING_UP, TripStatus.ARRIVED, TripStatus.IN_TRIP)
            );
            if (hasActiveTrip) {
                offer.setStatus("REJECTED");
                driverOfferRepository.save(offer);
                throw BaseDomainException.conflict("DRIVER_ALREADY_ASSIGNED", "Driver is already assigned to an active trip");
            }

            Trip trip = tripRepository.findById(offer.getTripId())
                    .orElseThrow(() -> BaseDomainException.notFound("TRIP_NOT_FOUND", "Trip not found"));

            if (trip.getStatus() != TripStatus.MATCHING) {
                offer.setStatus("REJECTED");
                driverOfferRepository.save(offer);
                throw BaseDomainException.conflict("TRIP_ALREADY_ASSIGNED", "Trip is no longer waiting for a driver");
            }

            // Assign driver atomically
            trip.setDriverId(driverId);
            trip.setStatus(TripStatus.ACCEPTED);
            trip.setAcceptedAt(Instant.now());
            tripRepository.save(trip);

            offer.setStatus("ACCEPTED");
            offer.setRespondedAt(Instant.now());
            driverOfferRepository.save(offer);

            log.info("Driver ID {} accepted Trip ID {} successfully", driverId, trip.getId());
            return toOfferResponse(offer);
        } finally {
            lockService.releaseLock(lockKey);
        }
    }

    @Override
    @Transactional
    public TripDetailResponse updateTripStatus(UUID tripId, UUID actorId, TripStatus nextStatus) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> BaseDomainException.notFound("TRIP_NOT_FOUND", "Trip not found"));

        validateStateTransition(trip.getStatus(), nextStatus);

        trip.setStatus(nextStatus);
        Instant now = Instant.now();

        if (nextStatus == TripStatus.IN_TRIP) {
            trip.setStartedAt(now);
        } else if (nextStatus == TripStatus.COMPLETED) {
            trip.setCompletedAt(now);

            // Session 10: Trigger Event-Driven messaging via Kafka
            TripCompletedEvent event = TripCompletedEvent.builder()
                    .tripId(trip.getId())
                    .tripCode(trip.getTripCode())
                    .customerId(trip.getCustomerId())
                    .driverId(trip.getDriverId())
                    .serviceType(trip.getServiceType())
                    .finalFare(trip.getFinalFare() != null ? trip.getFinalFare() : BigDecimal.ZERO)
                    .currency(trip.getCurrency())
                    .paymentMethod(trip.getPaymentMethod())
                    .completedAt(now)
                    .build();

            eventProducer.publishTripCompleted(event, UUID.randomUUID().toString());
        }

        trip = tripRepository.save(trip);
        log.info("Trip ID {} transitioned to status {}", tripId, nextStatus);
        return toResponse(trip);
    }

    @Override
    @Transactional(readOnly = true)
    public TripDetailResponse getTrip(UUID tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> BaseDomainException.notFound("TRIP_NOT_FOUND", "Trip not found"));
        return toResponse(trip);
    }

    private void validateStateTransition(TripStatus current, TripStatus next) {
        boolean valid = switch (current) {
            case CREATED -> next == TripStatus.MATCHING || next == TripStatus.CANCELLED;
            case MATCHING -> next == TripStatus.ACCEPTED || next == TripStatus.NO_DRIVER || next == TripStatus.CANCELLED;
            case ACCEPTED -> next == TripStatus.PICKING_UP || next == TripStatus.CANCELLED;
            case PICKING_UP -> next == TripStatus.ARRIVED || next == TripStatus.CANCELLED;
            case ARRIVED -> next == TripStatus.IN_TRIP || next == TripStatus.CANCELLED;
            case IN_TRIP -> next == TripStatus.COMPLETED || next == TripStatus.CANCELLED;
            case COMPLETED, CANCELLED, NO_DRIVER -> false;
        };

        if (!valid) {
            throw BaseDomainException.conflict(
                    "INVALID_TRIP_STATE",
                    String.format("Cannot transition trip from %s to %s", current, next)
            );
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
                t.getCompletedAt()
        );
    }

    private DriverOfferResponse toOfferResponse(DriverOffer o) {
        return new DriverOfferResponse(
                o.getId(),
                o.getTripId(),
                o.getDriverId(),
                o.getStatus(),
                o.getEstimatedPickupDistanceMeters(),
                o.getExpiresAt()
        );
    }
}
