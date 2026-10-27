package com.ridehailing.logistics.location.repository;

import com.ridehailing.logistics.location.domain.entity.DriverTelemetryHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverTelemetryHistoryRepository extends JpaRepository<DriverTelemetryHistory, Long> {
  List<DriverTelemetryHistory> findByDriverIdOrderByRecordedAtDesc(UUID driverId);
}
