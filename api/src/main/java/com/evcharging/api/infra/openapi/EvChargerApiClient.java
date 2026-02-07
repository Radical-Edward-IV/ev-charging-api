package com.evcharging.api.infra.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Collections;
import java.util.List;

@Component
public class EvChargerApiClient {

    private static final Logger log = LoggerFactory.getLogger(EvChargerApiClient.class);
    private static final String BASE_URL = "http://apis.data.go.kr/B552584/EvCharger";

    private final RestClient restClient;
    private final String serviceKey;

    public EvChargerApiClient(@Value("${openapi.service-key:}") String serviceKey) {
        this.restClient = RestClient.create();
        this.serviceKey = serviceKey;
    }

    public List<EvChargerApiResponse.Item> fetchSeoulChargers(int pageNo, int numOfRows) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("공공 API 서비스 키가 설정되지 않았습니다.");
            return Collections.emptyList();
        }

        try {
            // 공공데이터포털 API는 이미 인코딩된 ServiceKey를 직접 URL에 넣어야 함
            // UriBuilder.queryParam을 사용하면 이중 인코딩 발생
            String url = BASE_URL + "/getChargerInfo"
                    + "?ServiceKey=" + serviceKey
                    + "&pageNo=" + pageNo
                    + "&numOfRows=" + numOfRows
                    + "&zcode=11"
                    + "&dataType=JSON";

            EvChargerApiResponse response = restClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .body(EvChargerApiResponse.class);

            if (response != null && response.items() != null) {
                List<EvChargerApiResponse.Item> items = response.items().item();
                log.info("공공 API에서 {}건 조회 완료 (pageNo={}, numOfRows={})",
                        items.size(), pageNo, numOfRows);
                return items;
            }
        } catch (Exception e) {
            log.warn("공공 API 호출 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
