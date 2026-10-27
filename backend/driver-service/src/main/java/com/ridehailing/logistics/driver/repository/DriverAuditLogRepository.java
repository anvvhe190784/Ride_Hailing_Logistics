package com.ridehailing.logistics.driver.repository;

import com.ridehailing.logistics.driver.domain.entity.DriverAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverAuditLogRepository extends JpaRepository<DriverAuditLog, UUID> {
  List<DriverAuditLog> findByDriverId(UUID driverId);
}
