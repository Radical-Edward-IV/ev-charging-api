package com.evcharging.api.domain.charger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargerStatusTest {

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, CHARGING",
            "AVAILABLE, OUT_OF_SERVICE",
            "CHARGING, AVAILABLE",
            "CHARGING, OUT_OF_SERVICE",
            "OUT_OF_SERVICE, AVAILABLE"
    })
    void 유효한_상태_전이를_허용한다(ChargerStatus from, ChargerStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, AVAILABLE",
            "CHARGING, CHARGING",
            "OUT_OF_SERVICE, OUT_OF_SERVICE",
            "OUT_OF_SERVICE, CHARGING"
    })
    void 유효하지_않은_상태_전이를_거부한다(ChargerStatus from, ChargerStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void 유효하지_않은_전이_시_예외를_던진다() {
        assertThatThrownBy(() ->
                ChargerStatus.OUT_OF_SERVICE.validateTransition(ChargerStatus.CHARGING)
        ).isInstanceOf(IllegalStateException.class);
    }
}
