package com.evcharging.api.domain.charger;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.domain.session.ChargingSession;
import com.evcharging.api.domain.station.ChargingStation;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "charger")
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String chargerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerStatus status;

    private BigDecimal powerKw;

    @Enumerated(EnumType.STRING)
    private ConnectorType connectorType;

    private LocalDateTime lastStatusChangedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private ChargingStation station;

    @OneToMany(mappedBy = "charger", cascade = CascadeType.ALL)
    private List<ChargingSession> sessions = new ArrayList<>();

    protected Charger() {}

    public Charger(String chargerCode, ChargerType type, BigDecimal powerKw, ConnectorType connectorType) {
        this.chargerCode = chargerCode;
        this.type = type;
        this.status = ChargerStatus.AVAILABLE;
        this.powerKw = powerKw;
        this.connectorType = connectorType;
        this.lastStatusChangedAt = LocalDateTime.now();
    }

    public void changeStatus(ChargerStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        this.status = newStatus;
        this.lastStatusChangedAt = LocalDateTime.now();
    }

    public void assignStation(ChargingStation station) {
        this.station = station;
    }

    public Long getId() { return id; }
    public String getChargerCode() { return chargerCode; }
    public ChargerType getType() { return type; }
    public ChargerStatus getStatus() { return status; }
    public BigDecimal getPowerKw() { return powerKw; }
    public ConnectorType getConnectorType() { return connectorType; }
    public LocalDateTime getLastStatusChangedAt() { return lastStatusChangedAt; }
    public ChargingStation getStation() { return station; }
    public List<ChargingSession> getSessions() { return sessions; }
}
