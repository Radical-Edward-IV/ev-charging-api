package com.evcharging.api.infra.openapi;

import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerType;
import com.evcharging.api.domain.charger.ConnectorType;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StationRepository stationRepository;
    private final EvChargerApiClient apiClient;

    public DataSeeder(StationRepository stationRepository, EvChargerApiClient apiClient) {
        this.stationRepository = stationRepository;
        this.apiClient = apiClient;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stationRepository.count() > 0) {
            log.info("DB에 충전소 데이터가 이미 존재합니다. 시딩 건너뜀.");
            return;
        }

        List<EvChargerApiResponse.Item> items = apiClient.fetchSeoulChargers(1, 100);
        if (items.isEmpty()) {
            log.warn("공공 API에서 데이터를 가져오지 못했습니다. data.sql 폴백에 의존합니다.");
            return;
        }

        Map<String, ChargingStation> stationMap = new LinkedHashMap<>();
        for (EvChargerApiResponse.Item item : items) {
            ChargingStation station = stationMap.computeIfAbsent(item.statId(), statId ->
                    new ChargingStation(
                            statId,
                            item.statNm(),
                            item.addr(),
                            parseDouble(item.lat()),
                            parseDouble(item.lng()),
                            item.busiNm(),
                            item.busiCall(),
                            item.useTime()
                    ));

            Charger charger = new Charger(
                    item.chgerId(),
                    mapChargerType(item.chgerType()),
                    parsePowerKw(item.output()),
                    mapConnectorType(item.chgerType())
            );
            station.addCharger(charger);
        }

        stationRepository.saveAll(stationMap.values());
        log.info("공공 API에서 {}개 충전소, {}개 충전기를 시딩했습니다.",
                stationMap.size(), items.size());
    }

    private ChargerType mapChargerType(String chgerType) {
        if (chgerType == null) return ChargerType.AC_SLOW;
        return switch (chgerType) {
            case "01" -> ChargerType.DC_FAST;
            case "02" -> ChargerType.AC_SLOW;
            case "03" -> ChargerType.DC_COMBO;
            default -> ChargerType.AC_SLOW;
        };
    }

    private ConnectorType mapConnectorType(String chgerType) {
        if (chgerType == null) return ConnectorType.AC_TYPE_1;
        return switch (chgerType) {
            case "01" -> ConnectorType.CHADEMO;
            case "02" -> ConnectorType.AC_TYPE_1;
            case "03" -> ConnectorType.CCS1;
            default -> ConnectorType.AC_TYPE_1;
        };
    }

    private Double parseDouble(String value) {
        try {
            return value != null ? Double.parseDouble(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal parsePowerKw(String output) {
        try {
            return output != null ? new BigDecimal(output) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
