package com.ridehailing.logistics.trip.service;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.trip.dto.CreateTripRequest;
import com.ridehailing.logistics.trip.dto.DriverOfferResponse;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import java.util.UUID;

@SuppressWarnings("unused")
public interface TripService {
  TripDetailResponse createTrip(UUID customerId, CreateTripRequest request);

  DriverOfferResponse respondOffer(UUID offerId, UUID driverId, boolean accept);

  TripDetailResponse updateTripStatus(UUID tripId, UUID actorId, TripStatus nextStatus);

  TripDetailResponse getTrip(UUID tripId);
}
