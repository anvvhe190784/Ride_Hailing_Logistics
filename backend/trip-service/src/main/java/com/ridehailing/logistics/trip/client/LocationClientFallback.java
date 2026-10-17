package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.trip.dto.NearbyDriverResponse;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocationClientFallback implements LocationClient {

  @Override
  public ApiResponse<List<NearbyDriverResponse>> getNearbyDrivers(
      double latitude, double longitude, double radiusMeters, int limit) {
    log.warn("Fallback triggered for LocationClient.getNearbyDrivers");
    return ApiResponse.success(Collections.emptyList(), null);
  }
}
