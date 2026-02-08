package com.evcharging.api.api.station;

import com.evcharging.api.config.security.JwtTokenProvider;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StationController.class)
class StationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StationService stationService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser
    void findById_returns_station() throws Exception {
        ChargingStation station = new ChargingStation(
                "ST-001", "Gangnam Station", "Seoul Gangnam-gu",
                37.4979, 127.0276, "KEPCO", "02-1234-5678", "24h");
        given(stationService.findById(1L)).willReturn(station);

        mockMvc.perform(get("/api/v1/stations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Gangnam Station"))
                .andExpect(jsonPath("$.data.address").value("Seoul Gangnam-gu"));
    }
}
