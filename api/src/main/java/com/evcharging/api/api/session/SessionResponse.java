package com.evcharging.api.api.session;

import com.evcharging.api.domain.session.ChargingSession;
import com.evcharging.api.domain.session.SessionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionResponse(
        Long id,
        Long chargerId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal energyDeliveredKwh,
        BigDecimal cost,
        SessionStatus status
) {
    public static SessionResponse from(ChargingSession session) {
        return new SessionResponse(
                session.getId(),
                session.getCharger().getId(),
                session.getStartTime(),
                session.getEndTime(),
                session.getEnergyDeliveredKwh(),
                session.getCost(),
                session.getStatus()
        );
    }
}
