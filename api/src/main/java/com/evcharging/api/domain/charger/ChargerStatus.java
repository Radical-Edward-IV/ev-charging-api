package com.evcharging.api.domain.charger;

import java.util.Set;

public enum ChargerStatus {

    AVAILABLE(Set.of("CHARGING", "OUT_OF_SERVICE")),
    CHARGING(Set.of("AVAILABLE", "OUT_OF_SERVICE")),
    OUT_OF_SERVICE(Set.of("AVAILABLE"));

    private final Set<String> allowedTransitions;

    ChargerStatus(Set<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    public boolean canTransitionTo(ChargerStatus target) {
        return allowedTransitions.contains(target.name());
    }

    public void validateTransition(ChargerStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "%s에서 %s(으)로 전이할 수 없습니다".formatted(this.name(), target.name())
            );
        }
    }
}
