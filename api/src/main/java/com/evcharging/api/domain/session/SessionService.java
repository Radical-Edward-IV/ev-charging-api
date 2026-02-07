package com.evcharging.api.domain.session;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerRepository;
import com.evcharging.api.domain.charger.ChargerStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ChargerRepository chargerRepository;

    public SessionService(SessionRepository sessionRepository, ChargerRepository chargerRepository) {
        this.sessionRepository = sessionRepository;
        this.chargerRepository = chargerRepository;
    }

    @Transactional
    public ChargingSession startCharging(Long chargerId) {
        Charger charger = chargerRepository.findById(chargerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGER_NOT_FOUND));

        if (charger.getStatus() != ChargerStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.CHARGER_NOT_AVAILABLE);
        }

        charger.changeStatus(ChargerStatus.CHARGING);
        ChargingSession session = ChargingSession.start(charger);
        return sessionRepository.save(session);
    }

    @Transactional
    public ChargingSession completeCharging(Long sessionId, BigDecimal energyKwh, BigDecimal cost) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        session.complete(energyKwh, cost);
        session.getCharger().changeStatus(ChargerStatus.AVAILABLE);
        return session;
    }

    public List<ChargingSession> findByCharger(Long chargerId, LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null) {
            return sessionRepository.findByChargerIdAndStartTimeBetween(chargerId, start, end);
        }
        return sessionRepository.findByChargerId(chargerId);
    }
}
