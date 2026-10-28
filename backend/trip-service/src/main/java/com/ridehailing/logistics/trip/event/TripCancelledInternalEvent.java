package com.ridehailing.logistics.trip.event;

import com.ridehailing.logistics.trip.domain.entity.Trip;

public record TripCancelledInternalEvent(Trip trip) {}
