package com.evcharging.api.infra.openapi;

import java.util.List;

public record EvChargerApiResponse(
        String resultCode,
        String resultMsg,
        int totalCount,
        int pageNo,
        int numOfRows,
        Items items
) {
    public record Items(List<Item> item) {}

    public record Item(
            String statNm,
            String statId,
            String chgerId,
            String chgerType,
            String addr,
            String lat,
            String lng,
            String busiNm,
            String busiCall,
            String useTime,
            String stat,
            String output
    ) {}
}
