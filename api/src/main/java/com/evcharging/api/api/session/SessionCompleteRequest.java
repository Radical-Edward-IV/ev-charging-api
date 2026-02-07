package com.evcharging.api.api.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SessionCompleteRequest(
        @NotNull @Positive BigDecimal energyDeliveredKwh,
        @NotNull @Positive BigDecimal cost
) {}
