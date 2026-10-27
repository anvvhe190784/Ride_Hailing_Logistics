package com.ridehailing.logistics.trip.service;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.domain.entity.Trip;
import com.ridehailing.logistics.trip.dto.CreateTripRequest;
import com.ridehailing.logistics.trip.dto.DriverOfferResponse;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import com.ridehailing.logistics.trip.dto.response.CancellationFeeResponse;
import java.util.UUID;

@SuppressWarnings("unused")
public interface TripService {
  TripDetailResponse createTrip(UUID customerId, CreateTripRequest request);

  Trip saveInitialTrip(Trip trip);

  DriverOfferResponse respondOffer(UUID offerId, UUID driverId, boolean accept);

  default TripDetailResponse updateTripStatus(UUID tripId, UUID actorId, TripStatus nextStatus) {
    return updateTripStatus(tripId, actorId, null, nextStatus);
  }

  TripDetailResponse updateTripStatus(
      UUID tripId, UUID actorId, String actorRole, TripStatus nextStatus);

  default Trip updateTripStatusInDb(UUID tripId, UUID actorId, TripStatus nextStatus) {
    return updateTripStatusInDb(tripId, actorId, null, nextStatus);
  }

  Trip updateTripStatusInDb(
      UUID tripId, UUID actorId, String actorRole, TripStatus nextStatus);

  TripDetailResponse getTrip(UUID tripId);

  CancellationFeeResponse estimateCancellationFee(UUID tripId, UUID actorId, String actorRole);
}
