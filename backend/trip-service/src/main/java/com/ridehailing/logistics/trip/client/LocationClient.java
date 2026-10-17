package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.trip.dto.NearbyDriverResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@SuppressWarnings({"InjectedReferences", "unused"})
@FeignClient(
    name = "${feign.client.location-service.name:location-service}",
    contextId = "locationClient",
    fallback = LocationClientFallback.class)
public interface LocationClient {

  @GetMapping("/api/v1/locations/nearby-drivers")
  ApiResponse<List<NearbyDriverResponse>> getNearbyDrivers(
      @RequestParam("latitude") double latitude,
      @RequestParam("longitude") double longitude,
      @RequestParam(value = "radiusMeters", defaultValue = "3000") double radiusMeters,
      @RequestParam(value = "limit", defaultValue = "10") int limit);
}
