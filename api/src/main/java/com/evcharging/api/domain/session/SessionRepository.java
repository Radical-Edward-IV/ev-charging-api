package com.evcharging.api.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionRepository extends JpaRepository<ChargingSession, Long> {

    List<ChargingSession> findByChargerId(Long chargerId);

    List<ChargingSession> findByChargerIdAndStartTimeBetween(
            Long chargerId, LocalDateTime startDate, LocalDateTime endDate);
}
