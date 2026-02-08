package com.evcharging.api;

import com.evcharging.api.domain.member.Member;
import com.evcharging.api.domain.member.MemberRepository;
import com.evcharging.api.domain.member.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        memberRepository.deleteAll();

        // ADMIN 사용자 직접 생성
        Member admin = new Member("admin@test.com",
                passwordEncoder.encode("password123"), "Admin", Role.ADMIN);
        memberRepository.save(admin);

        // 로그인으로 토큰 획득
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@test.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginData = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("data");
        adminToken = loginData.get("accessToken").asText();
    }

    @Test
    void fullChargingFlow() throws Exception {
        // 1. Create a charging station (ADMIN)
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
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stationJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Test Station"))
                .andReturn();

        JsonNode stationData = objectMapper.readTree(
                stationResult.getResponse().getContentAsString()).get("data");
        long stationId = stationData.get("id").asLong();

        // 2. Add a charger (ADMIN)
        String chargerJson = """
                {
                    "chargerCode": "CHG-TEST-01",
                    "type": "DC_FAST",
                    "powerKw": 50,
                    "connectorType": "CCS1"
                }
                """;

        MvcResult chargerResult = mockMvc.perform(post("/api/v1/stations/" + stationId + "/chargers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chargerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();

        long chargerId = objectMapper.readTree(
                chargerResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        // 3. Start a charging session
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/chargers/" + chargerId + "/sessions")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();

        long sessionId = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        // 4. Verify charger is CHARGING
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/chargers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CHARGING"));

        // 5. Complete the session
        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/complete")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"energyDeliveredKwh": 35.5, "cost": 15000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        // 6. Verify charger is AVAILABLE
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/chargers")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));

        // 7. Query sessions
        mockMvc.perform(get("/api/v1/sessions?chargerId=" + chargerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 8. List stations
        mockMvc.perform(get("/api/v1/stations?page=0&size=10")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));

        // 9. Delete station (ADMIN)
        mockMvc.perform(delete("/api/v1/stations/" + stationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // 10. Verify deleted
        mockMvc.perform(get("/api/v1/stations/" + stationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidStatusTransition_returns400() throws Exception {
        String stationJson = """
                {"stationCode": "ST-TEST-002", "name": "Test Station 2", "address": "Seoul Jongno"}
                """;

        MvcResult stationResult = mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stationJson))
                .andExpect(status().isCreated())
                .andReturn();

        long stationId = objectMapper.readTree(
                stationResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        MvcResult chargerResult = mockMvc.perform(post("/api/v1/stations/" + stationId + "/chargers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"chargerCode": "CHG-TEST-02", "type": "AC_SLOW", "powerKw": 7, "connectorType": "AC_TYPE_1"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        long chargerId = objectMapper.readTree(
                chargerResult.getResponse().getContentAsString()).get("data").get("id").asLong();

        // AVAILABLE -> OUT_OF_SERVICE (valid)
        mockMvc.perform(patch("/api/v1/chargers/" + chargerId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "OUT_OF_SERVICE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OUT_OF_SERVICE"));

        // OUT_OF_SERVICE -> CHARGING (invalid)
        mockMvc.perform(patch("/api/v1/chargers/" + chargerId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CHARGING"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void validationError_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void stationNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/stations/99999")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("STATION_NOT_FOUND"));
    }
}
