package com.evcharging.api.domain.station;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StationRepository extends JpaRepository<ChargingStation, Long> {

    Page<ChargingStation> findAll(Pageable pageable);

    @Query(value = """
            SELECT s.* FROM charging_station s
            WHERE (6371 * acos(
                cos(radians(:lat)) * cos(radians(s.latitude))
                * cos(radians(s.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(s.latitude))
            )) < :radius
            ORDER BY (6371 * acos(
                cos(radians(:lat)) * cos(radians(s.latitude))
                * cos(radians(s.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(s.latitude))
            ))
            """, nativeQuery = true)
    List<ChargingStation> findNearby(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("radius") double radiusKm);
}
