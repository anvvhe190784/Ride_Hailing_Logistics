package com.ridehailing.logistics.driver.repository;

import com.ridehailing.logistics.driver.domain.entity.DriverDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverDocumentRepository extends JpaRepository<DriverDocument, UUID> {
  List<DriverDocument> findByDriverId(UUID driverId);
}
