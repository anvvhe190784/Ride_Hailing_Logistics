package com.ridehailing.logistics.trip.dto;

import java.time.Instant;
import java.util.UUID;

public record DriverOfferResponse(
        UUID offerId,
        UUID tripId,
        UUID driverId,
        String status,
        Integer estimatedPickupDistanceMeters,
        Instant expiresAt
) {}
