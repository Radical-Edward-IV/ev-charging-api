package com.evcharging.api.domain.session;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerRepository;
import com.evcharging.api.domain.charger.ChargerStatus;
import com.evcharging.api.domain.charger.ChargerType;
import com.evcharging.api.domain.charger.ConnectorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    SessionRepository sessionRepository;

    @Mock
    ChargerRepository chargerRepository;

    @InjectMocks
    SessionService sessionService;

    @Test
    void startCharging_available_charger_succeeds() {
        Charger charger = new Charger("CHG-001", ChargerType.DC_FAST,
                new BigDecimal("50"), ConnectorType.CCS1);
        given(chargerRepository.findById(1L)).willReturn(Optional.of(charger));
        given(sessionRepository.save(any(ChargingSession.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ChargingSession session = sessionService.startCharging(1L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(charger.getStatus()).isEqualTo(ChargerStatus.CHARGING);
    }

    @Test
    void startCharging_unavailable_charger_throws() {
        Charger charger = new Charger("CHG-001", ChargerType.DC_FAST,
                new BigDecimal("50"), ConnectorType.CCS1);
        charger.changeStatus(ChargerStatus.OUT_OF_SERVICE);
        given(chargerRepository.findById(1L)).willReturn(Optional.of(charger));

        assertThatThrownBy(() -> sessionService.startCharging(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void completeCharging_in_progress_session_succeeds() {
        Charger charger = new Charger("CHG-001", ChargerType.DC_FAST,
                new BigDecimal("50"), ConnectorType.CCS1);
        charger.changeStatus(ChargerStatus.CHARGING);
        ChargingSession session = ChargingSession.start(charger);

        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        ChargingSession completed = sessionService.completeCharging(
                1L, new BigDecimal("30.5"), new BigDecimal("15000"));

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getEnergyDeliveredKwh()).isEqualByComparingTo(new BigDecimal("30.5"));
        assertThat(charger.getStatus()).isEqualTo(ChargerStatus.AVAILABLE);
    }
}
