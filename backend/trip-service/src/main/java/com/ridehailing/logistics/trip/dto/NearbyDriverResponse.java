package com.ridehailing.logistics.trip.dto;

import java.util.UUID;

public record NearbyDriverResponse(
    UUID driverId, double latitude, double longitude, double distanceMeters) {}
