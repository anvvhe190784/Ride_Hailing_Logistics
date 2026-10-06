package com.ridehailing.logistics.location.service;

import com.ridehailing.logistics.location.dto.NearbyDriverResponse;
import com.ridehailing.logistics.location.dto.TelemetryRequest;
import java.util.List;
import java.util.UUID;

public interface LocationService {
  void processTelemetry(UUID driverId, TelemetryRequest request);

  List<NearbyDriverResponse> findNearbyDrivers(
      double latitude, double longitude, double radiusMeters, int limit);
}
