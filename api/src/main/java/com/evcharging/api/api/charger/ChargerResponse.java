package com.evcharging.api.api.charger;

import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerStatus;
import com.evcharging.api.domain.charger.ChargerType;
import com.evcharging.api.domain.charger.ConnectorType;

import java.math.BigDecimal;

public record ChargerResponse(
        Long id,
        String chargerCode,
        ChargerType type,
        ChargerStatus status,
        BigDecimal powerKw,
        ConnectorType connectorType
) {
    public static ChargerResponse from(Charger charger) {
        return new ChargerResponse(
                charger.getId(),
                charger.getChargerCode(),
                charger.getType(),
                charger.getStatus(),
                charger.getPowerKw(),
                charger.getConnectorType()
        );
    }
}
