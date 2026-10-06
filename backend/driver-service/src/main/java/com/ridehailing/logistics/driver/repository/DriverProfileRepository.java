package com.ridehailing.logistics.driver.repository;

import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {
}
