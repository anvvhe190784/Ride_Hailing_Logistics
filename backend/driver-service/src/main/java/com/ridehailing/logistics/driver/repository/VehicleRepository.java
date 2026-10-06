package com.ridehailing.logistics.driver.repository;

import com.ridehailing.logistics.driver.domain.entity.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
  List<Vehicle> findByDriverId(UUID driverId);

  Optional<Vehicle> findByLicensePlate(String licensePlate);

  boolean existsByLicensePlate(String licensePlate);
}
