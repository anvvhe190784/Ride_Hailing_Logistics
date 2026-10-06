package com.ridehailing.logistics.driver.repository;

import com.ridehailing.logistics.driver.domain.entity.DriverProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface DriverProfileRepository extends JpaRepository<DriverProfile, UUID> {}
