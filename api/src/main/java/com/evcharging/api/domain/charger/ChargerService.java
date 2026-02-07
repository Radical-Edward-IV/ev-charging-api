package com.evcharging.api.domain.charger;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChargerService {

    private final ChargerRepository chargerRepository;
    private final StationService stationService;

    public ChargerService(ChargerRepository chargerRepository, StationService stationService) {
        this.chargerRepository = chargerRepository;
        this.stationService = stationService;
    }

    public List<Charger> findByStation(Long stationId) {
        return chargerRepository.findByStationId(stationId);
    }

    @Transactional
    public Charger create(Long stationId, Charger charger) {
        ChargingStation station = stationService.findById(stationId);
        station.addCharger(charger);
        return chargerRepository.save(charger);
    }

    @Transactional
    public Charger changeStatus(Long chargerId, ChargerStatus newStatus) {
        Charger charger = chargerRepository.findById(chargerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGER_NOT_FOUND));
        charger.changeStatus(newStatus);
        return charger;
    }
}
