package com.ridehailing.logistics.trip.client;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.common.dto.ApiResponse;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "driver-service", fallback = DriverClientFallback.class)
public interface DriverClient {

  @GetMapping("/api/v1/drivers/{driverId}")
  ApiResponse<DriverProfileResponse> getDriverProfile(@PathVariable("driverId") UUID driverId);

  @PutMapping("/api/v1/drivers/availability")
  ApiResponse<DriverProfileResponse> updateDriverAvailability(
      @RequestParam("status") DriverAvailabilityStatus status);
}
