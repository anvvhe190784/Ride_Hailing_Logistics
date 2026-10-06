package com.ridehailing.logistics.driver.mapper;

import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import com.ridehailing.logistics.driver.domain.entity.Vehicle;
import com.ridehailing.logistics.common.dto.DriverProfileResponse;
import com.ridehailing.logistics.driver.dto.response.VehicleResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DriverMapper {
  DriverProfileResponse toResponse(DriverProfile profile);

  VehicleResponse toResponse(Vehicle vehicle);
}
