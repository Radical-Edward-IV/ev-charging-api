package com.evcharging.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "openapi.service-key="
})
class ChargingFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void fullChargingFlow() throws Exception {
        // 1. Create a charging station
        String stationJson = """
                {
                    "stationCode": "ST-TEST-001",
                    "name": "Test Station",
                    "address": "Seoul Gangnam",
                    "latitude": 37.4979,
                    "longitude": 127.0276,
                    "operatorName": "TestOp",
                    "contactNumber": "02-1234-5678",
                    "operatingHours": "24h"
                }
                """;

        MvcResult stationResult = mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stationJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Station"))
                .andReturn();

        JsonNode stationData = objectMapper.readTree(
                stationResult.getResponse().getContentAsString()).get("data");
        long stationId = stationData.get("id").asLong();

        // 2. Add a charger to the station
        String chargerJson = """
                {
                    "chargerCode": "CHG-TEST-01",
                    "type": "DC_FAST",
                    "powerKw": 50,
                    "connectorType": "CCS1"
                }
                """;

        MvcResult chargerResult = mockMvc.perform(post("/api/v1/stations/" + stationId + "/chargers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("DC_FAST"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();

        JsonNode chargerData = objectMapper.readTree(
                chargerResult.getResponse().getContentAsString()).get("data");
        long chargerId = chargerData.get("id").asLong();

        // 3. Start a charging session
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/chargers/" + chargerId + "/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();

        JsonNode sessionData = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString()).get("data");
        long sessionId = sessionData.get("id").asLong();

        // 4. Verify charger status changed to CHARGING
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/chargers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CHARGING"));

        // 5. Complete the charging session
        String completeJson = """
                {
                    "energyDeliveredKwh": 35.5,
                    "cost": 15000
                }
                """;

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completeJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.energyDeliveredKwh").value(35.5))
                .andExpect(jsonPath("$.data.cost").value(15000));

        // 6. Verify charger is back to AVAILABLE
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/chargers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));

        // 7. Query sessions for the charger
        mockMvc.perform(get("/api/v1/sessions?chargerId=" + chargerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"));

        // 8. List stations (paginated)
        mockMvc.perform(get("/api/v1/stations?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 9. Get station by ID
        mockMvc.perform(get("/api/v1/stations/" + stationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Station"));

        // 10. Delete the station
        mockMvc.perform(delete("/api/v1/stations/" + stationId))
                .andExpect(status().isNoContent());

        // 11. Verify station is deleted
        mockMvc.perform(get("/api/v1/stations/" + stationId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidStatusTransition_returns400() throws Exception {
        // Create station + charger
        String stationJson = """
                {
                    "stationCode": "ST-TEST-002",
                    "name": "Test Station 2",
                    "address": "Seoul Jongno"
                }
                """;

        MvcResult stationResult = mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stationJson))
                .andExpect(status().isCreated())
                .andReturn();

        long stationId = objectMapper.readTree(
                stationResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        String chargerJson = """
                {
                    "chargerCode": "CHG-TEST-02",
                    "type": "AC_SLOW",
                    "powerKw": 7,
                    "connectorType": "AC_TYPE_1"
                }
                """;

        MvcResult chargerResult = mockMvc.perform(post("/api/v1/stations/" + stationId + "/chargers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargerJson))
                .andExpect(status().isCreated())
                .andReturn();

        long chargerId = objectMapper.readTree(
                chargerResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        // Try to change AVAILABLE -> AVAILABLE (no-op, but might be allowed)
        // Try invalid: AVAILABLE cannot directly go to CHARGING via status endpoint
        // The charger starts as AVAILABLE; trying to set OUT_OF_SERVICE should succeed
        mockMvc.perform(patch("/api/v1/chargers/" + chargerId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "OUT_OF_SERVICE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_OF_SERVICE"));

        // Now try OUT_OF_SERVICE -> CHARGING (invalid transition)
        mockMvc.perform(patch("/api/v1/chargers/" + chargerId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CHARGING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void validationError_returns400() throws Exception {
        // Missing required fields
        mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void stationNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/stations/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("STATION_NOT_FOUND"));
    }
}
