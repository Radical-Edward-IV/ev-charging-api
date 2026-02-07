package com.evcharging.api.domain.session;

import com.evcharging.api.domain.charger.Charger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "charging_session")
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private BigDecimal energyDeliveredKwh;
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    protected ChargingSession() {}

    public static ChargingSession start(Charger charger) {
        ChargingSession session = new ChargingSession();
        session.charger = charger;
        session.startTime = LocalDateTime.now();
        session.status = SessionStatus.IN_PROGRESS;
        return session;
    }

    public void complete(BigDecimal energyDeliveredKwh, BigDecimal cost) {
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 세션만 완료할 수 있습니다");
        }
        this.endTime = LocalDateTime.now();
        this.energyDeliveredKwh = energyDeliveredKwh;
        this.cost = cost;
        this.status = SessionStatus.COMPLETED;
    }

    public Long getId() { return id; }
    public Charger getCharger() { return charger; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public BigDecimal getEnergyDeliveredKwh() { return energyDeliveredKwh; }
    public BigDecimal getCost() { return cost; }
    public SessionStatus getStatus() { return status; }
}
