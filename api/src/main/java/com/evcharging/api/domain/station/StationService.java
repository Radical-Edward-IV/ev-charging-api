package com.evcharging.api.domain.station;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    public Page<ChargingStation> findAll(Pageable pageable) {
        return stationRepository.findAll(pageable);
    }

    public ChargingStation findById(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.STATION_NOT_FOUND));
    }

    public List<ChargingStation> findNearby(double lat, double lng, double radiusKm) {
        return stationRepository.findNearby(lat, lng, radiusKm);
    }

    @Transactional
    public ChargingStation create(ChargingStation station) {
        return stationRepository.save(station);
    }

    @Transactional
    public ChargingStation update(Long id, String name, String address, Double latitude, Double longitude,
                                  String operatorName, String contactNumber, String operatingHours) {
        ChargingStation station = findById(id);
        station.update(name, address, latitude, longitude, operatorName, contactNumber, operatingHours);
        return station;
    }

    @Transactional
    public void delete(Long id) {
        ChargingStation station = findById(id);
        stationRepository.delete(station);
    }
}
