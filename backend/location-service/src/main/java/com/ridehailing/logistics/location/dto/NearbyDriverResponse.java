package com.ridehailing.logistics.location.dto;

import java.util.UUID;

public record NearbyDriverResponse(
        UUID driverId,
        double latitude,
        double longitude,
        double distanceMeters
) {}
