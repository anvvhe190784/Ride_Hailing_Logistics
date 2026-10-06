package com.ridehailing.logistics.trip.controller;

import com.ridehailing.logistics.common.domain.enums.TripStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.security.SecurityConstants;
import com.ridehailing.logistics.trip.dto.CreateTripRequest;
import com.ridehailing.logistics.trip.dto.DriverOfferResponse;
import com.ridehailing.logistics.trip.dto.RespondOfferRequest;
import com.ridehailing.logistics.trip.dto.TripDetailResponse;
import com.ridehailing.logistics.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<ApiResponse<TripDetailResponse>> createTrip(
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID customerId,
            @Valid @RequestBody CreateTripRequest request,
            @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        TripDetailResponse response = tripService.createTrip(customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, correlationId));
    }

    @GetMapping("/{tripId}")
    public ResponseEntity<ApiResponse<TripDetailResponse>> getTrip(
            @PathVariable UUID tripId,
            @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        TripDetailResponse response = tripService.getTrip(tripId);
        return ResponseEntity.ok(ApiResponse.success(response, correlationId));
    }

    @PutMapping("/{tripId}/status")
    public ResponseEntity<ApiResponse<TripDetailResponse>> updateTripStatus(
            @PathVariable UUID tripId,
            @RequestParam TripStatus nextStatus,
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID actorId,
            @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        TripDetailResponse response = tripService.updateTripStatus(tripId, actorId, nextStatus);
        return ResponseEntity.ok(ApiResponse.success(response, correlationId));
    }

    @PostMapping("/offers/{offerId}/respond")
    public ResponseEntity<ApiResponse<DriverOfferResponse>> respondOffer(
            @PathVariable UUID offerId,
            @RequestHeader(SecurityConstants.HEADER_USER_ID) UUID driverId,
            @RequestBody RespondOfferRequest request,
            @RequestHeader(value = SecurityConstants.HEADER_CORRELATION_ID, required = false) String correlationId) {
        DriverOfferResponse response = tripService.respondOffer(offerId, driverId, request.accept());
        return ResponseEntity.ok(ApiResponse.success(response, correlationId));
    }
}
