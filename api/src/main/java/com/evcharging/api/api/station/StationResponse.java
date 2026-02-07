package com.evcharging.api.api.station;

import com.evcharging.api.api.charger.ChargerResponse;
import com.evcharging.api.domain.station.ChargingStation;

import java.util.List;

public record StationResponse(
        Long id,
        String stationCode,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String operatorName,
        String contactNumber,
        String operatingHours,
        List<ChargerResponse> chargers
) {
    public static StationResponse from(ChargingStation station) {
        return new StationResponse(
                station.getId(),
                station.getStationCode(),
                station.getName(),
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.getOperatorName(),
                station.getContactNumber(),
                station.getOperatingHours(),
                station.getChargers().stream().map(ChargerResponse::from).toList()
        );
    }
}
