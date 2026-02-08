# Spring Security JWT 인증/인가 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** EV 충전소 API에 JWT 기반 인증/인가를 추가하여 회원가입, 로그인, 역할 기반 접근 제어를 구현한다.

**Architecture:** Access Token만 사용하는 Stateless JWT 인증. Spring Security 필터 체인에 커스텀 JWT 필터를 등록하고, ADMIN/USER 역할로 API 접근을 제어한다.

**Tech Stack:** Spring Boot 4.0.2, Spring Security 7, JJWT 0.12.6, BCrypt, Testcontainers

**중요:** 모든 명령은 컨테이너 내부에서 실행: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ..."`
**중요:** Spring Boot 4.x에서 `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure` 패키지

---

### Task 1: 의존성 추가 및 JWT 설정

**Files:**
- Modify: `api/build.gradle`
- Modify: `api/src/main/resources/application.yml`

**Step 1: build.gradle에 Security + JJWT 의존성 추가**

`dependencies` 블록에 추가:
```groovy
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'

testImplementation 'org.springframework.boot:spring-boot-starter-security-test'
```

**Step 2: application.yml에 JWT 설정 추가**

`application.yml` 하단에 추가:
```yaml
jwt:
  secret: ${JWT_SECRET:my-super-secret-key-for-ev-charging-api-that-is-at-least-256-bits-long}
  expiration-ms: 3600000
```

**Step 3: 컴파일 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew compileJava 2>&1 | tail -5"`
Expected: BUILD SUCCESSFUL (Security 자동 설정이 동작하므로 기존 테스트는 실패할 수 있음 — 이 시점에서는 컴파일만 확인)

**Step 4: 커밋 요청**
```
chore: Spring Security + JJWT 의존성 및 JWT 설정 추가
```

---

### Task 2: Member 도메인 (Role, Member, MemberRepository)

**Files:**
- Create: `api/src/main/java/com/evcharging/api/domain/member/Role.java`
- Create: `api/src/main/java/com/evcharging/api/domain/member/Member.java`
- Create: `api/src/main/java/com/evcharging/api/domain/member/MemberRepository.java`

**Step 1: Role enum 생성**

```java
package com.evcharging.api.domain.member;

public enum Role {
    ADMIN, USER
}
```

**Step 2: Member 엔티티 생성**

```java
package com.evcharging.api.domain.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private LocalDateTime createdAt;

    protected Member() {}

    public Member(String email, String password, String name, Role role) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.role = role;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
```

**Step 3: MemberRepository 생성**

```java
package com.evcharging.api.domain.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

**Step 4: 컴파일 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew compileJava 2>&1 | tail -3"`
Expected: BUILD SUCCESSFUL

**Step 5: 커밋 요청**
```
feat: Member 도메인 엔티티, Role enum, MemberRepository 추가
```

---

### Task 3: ErrorCode 추가 및 Auth DTO

**Files:**
- Modify: `api/src/main/java/com/evcharging/api/common/ErrorCode.java`
- Create: `api/src/main/java/com/evcharging/api/api/auth/SignupRequest.java`
- Create: `api/src/main/java/com/evcharging/api/api/auth/SignupResponse.java`
- Create: `api/src/main/java/com/evcharging/api/api/auth/LoginRequest.java`
- Create: `api/src/main/java/com/evcharging/api/api/auth/LoginResponse.java`

**Step 1: ErrorCode에 인증 관련 코드 추가**

`SESSION_ALREADY_COMPLETED` 뒤에 추가:
```java
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
```
(기존 `SESSION_ALREADY_COMPLETED` 뒤의 세미콜론을 콤마로 변경)

**Step 2: SignupRequest 생성**

```java
package com.evcharging.api.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String name
) {}
```

**Step 3: SignupResponse 생성**

```java
package com.evcharging.api.api.auth;

import com.evcharging.api.domain.member.Member;

public record SignupResponse(Long id, String email, String name) {
    public static SignupResponse from(Member member) {
        return new SignupResponse(member.getId(), member.getEmail(), member.getName());
    }
}
```

**Step 4: LoginRequest 생성**

```java
package com.evcharging.api.api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {}
```

**Step 5: LoginResponse 생성**

```java
package com.evcharging.api.api.auth;

public record LoginResponse(String accessToken, String tokenType) {
    public static LoginResponse of(String accessToken) {
        return new LoginResponse(accessToken, "Bearer");
    }
}
```

**Step 6: 컴파일 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew compileJava 2>&1 | tail -3"`
Expected: BUILD SUCCESSFUL

**Step 7: 커밋 요청**
```
feat: 인증 관련 ErrorCode 및 Auth DTO 추가
```

---

### Task 4: JwtTokenProvider TDD

**Files:**
- Test: `api/src/test/java/com/evcharging/api/config/security/JwtTokenProviderTest.java`
- Create: `api/src/main/java/com/evcharging/api/config/security/JwtTokenProvider.java`

**Step 1: JwtTokenProvider 테스트 작성**

```java
package com.evcharging.api.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(
                "my-super-secret-key-for-ev-charging-api-that-is-at-least-256-bits-long",
                3600000L);
    }

    @Test
    void generateToken_and_extractClaims() {
        String token = provider.generateToken("test@example.com", "USER");

        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getEmail(token)).isEqualTo("test@example.com");
        assertThat(provider.getRole(token)).isEqualTo("USER");
    }

    @Test
    void validateToken_invalidToken_returnsFalse() {
        assertThat(provider.validateToken("invalid.token.here")).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "my-super-secret-key-for-ev-charging-api-that-is-at-least-256-bits-long",
                -1000L); // 이미 만료
        String token = shortLived.generateToken("test@example.com", "USER");

        assertThat(provider.validateToken(token)).isFalse();
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test --tests 'com.evcharging.api.config.security.JwtTokenProviderTest' 2>&1 | tail -5"`
Expected: FAIL (클래스 없음)

**Step 3: JwtTokenProvider 구현**

```java
package com.evcharging.api.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test --tests 'com.evcharging.api.config.security.JwtTokenProviderTest' 2>&1 | tail -5"`
Expected: BUILD SUCCESSFUL

**Step 5: 커밋 요청**
```
feat: JwtTokenProvider 구현 (TDD)
```

---

### Task 5: AuthService TDD

**Files:**
- Test: `api/src/test/java/com/evcharging/api/domain/member/AuthServiceTest.java`
- Create: `api/src/main/java/com/evcharging/api/domain/member/AuthService.java`

**Step 1: AuthService 테스트 작성**

```java
package com.evcharging.api.domain.member;

import com.evcharging.api.api.auth.LoginRequest;
import com.evcharging.api.api.auth.LoginResponse;
import com.evcharging.api.api.auth.SignupRequest;
import com.evcharging.api.api.auth.SignupResponse;
import com.evcharging.api.common.BusinessException;
import com.evcharging.api.config.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    MemberRepository memberRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthService authService;

    @Test
    void signup_success() {
        given(memberRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("encoded");
        given(memberRepository.save(any(Member.class))).willAnswer(inv -> inv.getArgument(0));

        SignupResponse response = authService.signup(
                new SignupRequest("test@example.com", "password123", "Test User"));

        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("Test User");
    }

    @Test
    void signup_duplicateEmail_throws() {
        given(memberRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.signup(
                new SignupRequest("test@example.com", "password123", "Test User")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_success() {
        Member member = new Member("test@example.com", "encoded", "Test User", Role.USER);
        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("password123", "encoded")).willReturn(true);
        given(jwtTokenProvider.generateToken("test@example.com", "USER")).willReturn("jwt-token");

        LoginResponse response = authService.login(
                new LoginRequest("test@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
    }

    @Test
    void login_wrongPassword_throws() {
        Member member = new Member("test@example.com", "encoded", "Test User", Role.USER);
        given(memberRepository.findByEmail("test@example.com")).willReturn(Optional.of(member));
        given(passwordEncoder.matches("wrongpass", "encoded")).willReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("test@example.com", "wrongpass")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void login_emailNotFound_throws() {
        given(memberRepository.findByEmail("noone@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("noone@example.com", "password123")))
                .isInstanceOf(BusinessException.class);
    }
}
```

**Step 2: 테스트 실패 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test --tests 'com.evcharging.api.domain.member.AuthServiceTest' 2>&1 | tail -5"`
Expected: FAIL (클래스 없음)

**Step 3: AuthService 구현**

```java
package com.evcharging.api.domain.member;

import com.evcharging.api.api.auth.LoginRequest;
import com.evcharging.api.api.auth.LoginResponse;
import com.evcharging.api.api.auth.SignupRequest;
import com.evcharging.api.api.auth.SignupResponse;
import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.config.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        Member member = new Member(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                Role.USER);

        memberRepository.save(member);
        return SignupResponse.from(member);
    }

    public LoginResponse login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), member.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtTokenProvider.generateToken(member.getEmail(), member.getRole().name());
        return LoginResponse.of(token);
    }
}
```

**Step 4: 테스트 통과 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test --tests 'com.evcharging.api.domain.member.AuthServiceTest' 2>&1 | tail -5"`
Expected: BUILD SUCCESSFUL

**Step 5: 커밋 요청**
```
feat: AuthService 구현 (TDD) - 회원가입, 로그인
```

---

### Task 6: Security 설정 (SecurityConfig, JwtAuthenticationFilter)

**Files:**
- Create: `api/src/main/java/com/evcharging/api/config/security/JwtAuthenticationFilter.java`
- Create: `api/src/main/java/com/evcharging/api/config/security/SecurityConfig.java`

**Step 1: JwtAuthenticationFilter 구현**

```java
package com.evcharging.api.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String email = jwtTokenProvider.getEmail(token);
            String role = jwtTokenProvider.getRole(token);

            var authentication = new UsernamePasswordAuthenticationToken(
                    email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
```

**Step 2: SecurityConfig 구현**

```java
package com.evcharging.api.config.security;

import com.evcharging.api.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/stations").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/stations/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            objectMapper.writeValue(response.getOutputStream(),
                                    ApiResponse.error("UNAUTHORIZED", "인증이 필요합니다"));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            objectMapper.writeValue(response.getOutputStream(),
                                    ApiResponse.error("FORBIDDEN", "접근 권한이 없습니다"));
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Step 3: 컴파일 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew compileJava 2>&1 | tail -5"`
Expected: BUILD SUCCESSFUL

**Step 4: 커밋 요청**
```
feat: SecurityConfig + JwtAuthenticationFilter 구현
```

---

### Task 7: AuthController

**Files:**
- Create: `api/src/main/java/com/evcharging/api/api/auth/AuthController.java`

**Step 1: AuthController 구현**

```java
package com.evcharging.api.api.auth;

import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.member.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
```

**Step 2: 컴파일 확인**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew compileJava 2>&1 | tail -3"`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋 요청**
```
feat: AuthController 구현 (signup, login)
```

---

### Task 8: 기존 테스트 수정

**Files:**
- Modify: `api/src/test/java/com/evcharging/api/api/station/StationControllerTest.java`
- Modify: `api/src/test/java/com/evcharging/api/ChargingFlowIntegrationTest.java`
- Modify: `api/src/test/java/com/evcharging/api/ApiApplicationTests.java`

**Step 1: StationControllerTest에 Security 처리 추가**

`@WebMvcTest`에서는 `SecurityConfig`가 로드되므로 `JwtTokenProvider`를 mock 해야 함.
기존 `StationControllerTest.java`를 아래로 교체:

```java
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
```

**Step 2: ChargingFlowIntegrationTest에 인증 플로우 추가**

기존 `ChargingFlowIntegrationTest.java`를 아래로 교체.
핵심 변경: ADMIN 사용자 생성 → 로그인 → 토큰을 모든 요청에 포함.

```java
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
```

**Step 3: ApiApplicationTests에 jwt 속성 확인**

기존 `@TestPropertySource`에 jwt 기본값이 application.yml에 있으므로 변경 불필요. 다만 컨텍스트 로드 시 `PasswordEncoder` 빈이 `SecurityConfig`에서 제공되므로 문제없음.

**Step 4: 전체 테스트 실행**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test 2>&1 | tail -10"`
Expected: BUILD SUCCESSFUL (모든 기존 테스트 + JwtTokenProvider + AuthService 테스트 통과)

**Step 5: 커밋 요청**
```
fix: 기존 테스트에 Security 인증 처리 추가
```

---

### Task 9: AuthController 테스트

**Files:**
- Create: `api/src/test/java/com/evcharging/api/api/auth/AuthControllerTest.java`

**Step 1: AuthControllerTest 작성**

```java
package com.evcharging.api.api.auth;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.config.security.JwtTokenProvider;
import com.evcharging.api.domain.member.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @Test
    void signup_success() throws Exception {
        given(authService.signup(any(SignupRequest.class)))
                .willReturn(new SignupResponse(1L, "test@example.com", "Tester"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@example.com", "password": "password123", "name": "Tester"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@example.com"))
                .andExpect(jsonPath("$.data.name").value("Tester"));
    }

    @Test
    void signup_duplicateEmail_returns409() throws Exception {
        given(authService.signup(any(SignupRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "dup@example.com", "password": "password123", "name": "Dup"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void signup_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "password123", "name": "Test"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void login_success() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willReturn(LoginResponse.of("jwt-token-here"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@example.com", "password": "password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token-here"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "test@example.com", "password": "wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }
}
```

**Step 2: 테스트 실행**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test --tests 'com.evcharging.api.api.auth.AuthControllerTest' 2>&1 | tail -5"`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋 요청**
```
test: AuthController 테스트 (signup, login, 에러 케이스)
```

---

### Task 10: Security 통합 테스트

**Files:**
- Create: `api/src/test/java/com/evcharging/api/SecurityIntegrationTest.java`

**Step 1: SecurityIntegrationTest 작성**

```java
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
```

**Step 2: 테스트 실행**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew test --tests 'com.evcharging.api.SecurityIntegrationTest' 2>&1 | tail -5"`
Expected: BUILD SUCCESSFUL

**Step 3: 커밋 요청**
```
test: Security 통합 테스트 (401, 403, ADMIN/USER 접근 제어)
```

---

### Task 11: Swagger Bearer 토큰 설정

**Files:**
- Modify: `api/src/main/java/com/evcharging/api/config/SwaggerConfig.java`

**Step 1: SwaggerConfig에 Bearer 인증 스키마 추가**

```java
package com.evcharging.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI evChargingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EV 충전소 관리 API")
                        .description("한국환경공단 공공 API 기반의 EV 충전소 관리 REST API")
                        .version("v1.0.0")
                        .contact(new Contact().name("Kim Gwanghyeok")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
```

**Step 2: 전체 테스트 실행**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew clean test 2>&1 | tail -10"`
Expected: BUILD SUCCESSFUL (모든 테스트 통과)

**Step 3: 커밋 요청**
```
feat: Swagger UI에 Bearer JWT 인증 스키마 추가
```

---

### Task 12: 최종 검증

**Step 1: clean build**

Run: `docker exec bda5a5a44cf6 bash -c "cd /workspace/api && ./gradlew clean build 2>&1 | tail -10"`
Expected: BUILD SUCCESSFUL

**Step 2: 앱 기동 및 수동 검증**

Run: `docker exec -e OPEN_API_KEY='...' bda5a5a44cf6 bash -c "cd /workspace/api && timeout 30 ./gradlew bootRun --args='--spring.profiles.active=local' 2>&1 | grep -E '(Started|ERROR)' | head -5"`
Expected: `Started ApiApplication`

**Step 3: 기능 검증 (curl)**

```bash
# 회원가입
curl -s -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"test@ev.com","password":"password123","name":"Tester"}'

# 로그인
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@ev.com","password":"password123"}'

# 토큰 없이 → 401
curl -s http://localhost:8080/api/v1/stations

# 토큰 포함 → 200
curl -s http://localhost:8080/api/v1/stations \
  -H "Authorization: Bearer <token>"
```

**Step 4: 최종 커밋 요청**
```
feat: Spring Security JWT 인증/인가 구현 완료
```

---

## 완료 기준 체크리스트

- [ ] `./gradlew clean test` 전체 통과
- [ ] Swagger UI에서 signup, login 확인 가능
- [ ] 토큰 없이 요청 시 401
- [ ] 유효 토큰 시 정상 응답
- [ ] USER로 POST/DELETE stations 시 403
- [ ] ADMIN은 모든 API 접근 가능
- [ ] 기존 테스트 모두 통과
