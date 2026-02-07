package com.evcharging.api.api.charger;

import com.evcharging.api.domain.charger.ChargerStatus;
import jakarta.validation.constraints.NotNull;

public record ChargerStatusRequest(
        @NotNull ChargerStatus status
) {}
