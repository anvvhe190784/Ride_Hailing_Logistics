package com.ridehailing.logistics.location.repository;

import com.ridehailing.logistics.location.domain.entity.DriverLatestLocation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@SuppressWarnings("unused")
public interface DriverLatestLocationRepository extends JpaRepository<DriverLatestLocation, UUID> {

  @Query(
      value =
          """
        SELECT d.driver_id AS driverId,
               CAST(d.latitude AS float8) AS latitude,
               CAST(d.longitude AS float8) AS longitude,
               CAST(ST_Distance(d.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS float8) AS distanceMeters
        FROM location.driver_latest_locations d
        WHERE (d.is_available = TRUE OR d.updated_at >= :cutoffTime)
          AND ST_DWithin(d.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
        ORDER BY distanceMeters ASC
        LIMIT :limit
        """,
      nativeQuery = true)
  List<NearbyDriverProjection> findAvailableDriversNearby(
      @Param("lat") double latitude,
      @Param("lng") double longitude,
      @Param("radiusMeters") double radiusMeters,
      @Param("cutoffTime") Instant cutoffTime,
      @Param("limit") int limit);

  interface NearbyDriverProjection {
    UUID getDriverId();

    Double getLatitude();

    Double getLongitude();

    Double getDistanceMeters();
  }
}
