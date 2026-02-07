package com.evcharging.api.domain.station;

import com.evcharging.api.domain.charger.Charger;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "charging_station", indexes = {
        @Index(name = "idx_station_lat_lng", columnList = "latitude, longitude")
})
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String stationCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private Double latitude;
    private Double longitude;

    private String operatorName;
    private String contactNumber;
    private String operatingHours;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<Charger> chargers = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected ChargingStation() {}

    public ChargingStation(String stationCode, String name, String address,
                           Double latitude, Double longitude,
                           String operatorName, String contactNumber, String operatingHours) {
        this.stationCode = stationCode;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.operatorName = operatorName;
        this.contactNumber = contactNumber;
        this.operatingHours = operatingHours;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, String address, Double latitude, Double longitude,
                       String operatorName, String contactNumber, String operatingHours) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.operatorName = operatorName;
        this.contactNumber = contactNumber;
        this.operatingHours = operatingHours;
    }

    public void addCharger(Charger charger) {
        chargers.add(charger);
        charger.assignStation(this);
    }

    public Long getId() { return id; }
    public String getStationCode() { return stationCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getOperatorName() { return operatorName; }
    public String getContactNumber() { return contactNumber; }
    public String getOperatingHours() { return operatingHours; }
    public List<Charger> getChargers() { return chargers; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
