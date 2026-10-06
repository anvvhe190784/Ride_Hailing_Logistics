package com.ridehailing.logistics.location.repository;

import com.ridehailing.logistics.location.domain.entity.DriverLatestLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DriverLatestLocationRepository extends JpaRepository<DriverLatestLocation, UUID> {

    @Query(value = """
        SELECT d.driver_id AS driverId,
               d.latitude AS latitude,
               d.longitude AS longitude,
               ROUND(CAST(ST_Distance(d.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS numeric), 2) AS distanceMeters
        FROM location.driver_latest_locations d
        JOIN driver.driver_profiles p ON d.driver_id = p.driver_id
        WHERE p.availability_status = 'AVAILABLE'
          AND p.review_status = 'APPROVED'
          AND ST_DWithin(d.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
        ORDER BY distanceMeters ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<NearbyDriverProjection> findAvailableDriversNearby(
            @Param("lat") double latitude,
            @Param("lng") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("limit") int limit
    );

    interface NearbyDriverProjection {
        UUID getDriverId();
        double getLatitude();
        double getLongitude();
        double getDistanceMeters();
    }
}
