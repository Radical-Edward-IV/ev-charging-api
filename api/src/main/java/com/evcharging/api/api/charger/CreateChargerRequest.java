package com.evcharging.api.api.charger;

import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerType;
import com.evcharging.api.domain.charger.ConnectorType;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateChargerRequest(
        String chargerCode,
        @NotNull ChargerType type,
        BigDecimal powerKw,
        ConnectorType connectorType
) {
    public Charger toEntity() {
        return new Charger(chargerCode, type, powerKw, connectorType);
    }
}
