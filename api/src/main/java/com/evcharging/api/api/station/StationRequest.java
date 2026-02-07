package com.evcharging.api.api.station;

import com.evcharging.api.domain.station.ChargingStation;
import jakarta.validation.constraints.NotBlank;

public record StationRequest(
        String stationCode,
        @NotBlank String name,
        @NotBlank String address,
        Double latitude,
        Double longitude,
        String operatorName,
        String contactNumber,
        String operatingHours
) {
    public ChargingStation toEntity() {
        return new ChargingStation(stationCode, name, address, latitude, longitude,
                operatorName, contactNumber, operatingHours);
    }
}
