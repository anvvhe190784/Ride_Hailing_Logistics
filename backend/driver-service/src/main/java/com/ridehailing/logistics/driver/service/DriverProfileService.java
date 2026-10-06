package com.ridehailing.logistics.driver.service;

import com.ridehailing.logistics.common.domain.enums.DriverAvailabilityStatus;
import com.ridehailing.logistics.driver.dto.request.DriverKycRequest;
import com.ridehailing.logistics.driver.dto.request.VehicleCreateRequest;
import com.ridehailing.logistics.driver.dto.response.DriverProfileResponse;
import com.ridehailing.logistics.driver.dto.response.VehicleResponse;

import java.util.List;
import java.util.UUID;

public interface DriverProfileService {
    DriverProfileResponse submitKyc(UUID driverId, DriverKycRequest request);
    DriverProfileResponse approveDriver(UUID driverId, UUID reviewerId);
    DriverProfileResponse updateAvailability(UUID driverId, DriverAvailabilityStatus status);
    DriverProfileResponse getProfile(UUID driverId);
    VehicleResponse registerVehicle(UUID driverId, VehicleCreateRequest request);
    List<VehicleResponse> getVehicles(UUID driverId);
}
