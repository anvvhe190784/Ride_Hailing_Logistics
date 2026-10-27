package com.ridehailing.logistics.trip.event;

import com.ridehailing.logistics.common.domain.enums.PaymentMethod;
import com.ridehailing.logistics.common.domain.enums.ServiceType;
import com.ridehailing.logistics.common.event.TripCompletedEvent;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TripCompletedInternalEvent(
    UUID tripId,
    String tripCode,
    UUID customerId,
    UUID driverId,
    ServiceType serviceType,
    BigDecimal finalFare,
    String currency,
    PaymentMethod paymentMethod,
    Instant completedAt,
    Trip trip
) {
  public TripCompletedInternalEvent(Trip trip) {
    this(
        trip.getId(),
        trip.getTripCode(),
        trip.getCustomerId(),
        trip.getDriverId(),
        trip.getServiceType(),
        trip.getFinalFare() != null ? trip.getFinalFare() : BigDecimal.ZERO,
        trip.getCurrency(),
        trip.getPaymentMethod(),
        trip.getCompletedAt() != null ? trip.getCompletedAt() : Instant.now(),
        trip);
  }

  public TripCompletedEvent toTripCompletedEvent() {
    return TripCompletedEvent.builder()
        .tripId(tripId)
        .tripCode(tripCode)
        .customerId(customerId)
        .driverId(driverId)
        .serviceType(serviceType)
        .finalFare(finalFare != null ? finalFare : BigDecimal.ZERO)
        .currency(currency)
        .paymentMethod(paymentMethod)
        .completedAt(completedAt != null ? completedAt : Instant.now())
        .build();
  }
}
