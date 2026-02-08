package com.evcharging.api;

import com.evcharging.api.domain.member.Member;
import com.evcharging.api.domain.member.MemberRepository;
import com.evcharging.api.domain.member.Role;
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
class SecurityIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void unauthenticated_request_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticated_user_can_access_get_endpoints() throws Exception {
        String token = createUserAndLogin("user@test.com", "password123", Role.USER);

        mockMvc.perform(get("/api/v1/stations?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void user_cannot_create_station_returns403() throws Exception {
        String token = createUserAndLogin("user@test.com", "password123", Role.USER);

        mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode": "ST-001", "name": "Test", "address": "Seoul"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void user_cannot_delete_station_returns403() throws Exception {
        String token = createUserAndLogin("user@test.com", "password123", Role.USER);

        mockMvc.perform(delete("/api/v1/stations/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void admin_can_create_station() throws Exception {
        String token = createUserAndLogin("admin@test.com", "password123", Role.ADMIN);

        mockMvc.perform(post("/api/v1/stations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode": "ST-001", "name": "Admin Station", "address": "Seoul"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Admin Station"));
    }

    @Test
    void signup_and_login_flow() throws Exception {
        // 회원가입
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "new@test.com", "password": "password123", "name": "New User"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new@test.com"));

        // 로그인
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "new@test.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andReturn();

        // 획득한 토큰으로 API 접근
        String token = objectMapper.readTree(
                loginResult.getResponse().getContentAsString()).get("data").get("accessToken").asText();

        mockMvc.perform(get("/api/v1/stations?page=0&size=10")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void auth_endpoints_accessible_without_token() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@test.com", "password": "password123", "name": "Test"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void invalid_token_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/stations")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }

    private String createUserAndLogin(String email, String password, Role role) throws Exception {
        Member member = new Member(email, passwordEncoder.encode(password), "Test", role);
        memberRepository.save(member);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(
                result.getResponse().getContentAsString()).get("data").get("accessToken").asText();
    }
}
