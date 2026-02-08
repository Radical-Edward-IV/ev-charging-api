# Spring Security JWT 인증/인가 설계

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:writing-plans to create the implementation plan, then superpowers:executing-plans to implement.

**Goal:** EV 충전소 API에 JWT 기반 인증/인가를 추가하여 회원가입, 로그인, 역할 기반 접근 제어를 구현한다.

**Architecture:** Access Token만 사용하는 Stateless JWT 인증. Spring Security 필터 체인에 커스텀 JWT 필터를 등록하고, ADMIN/USER 역할로 API 접근을 제어한다.

**Tech Stack:** Spring Security, JJWT 0.12.6, BCrypt

---

## 1. 패키지 구조

```
com.evcharging.api
├── domain/member/
│   ├── Member.java
│   ├── Role.java
│   └── MemberRepository.java
├── api/auth/
│   ├── AuthController.java
│   ├── SignupRequest.java
│   ├── SignupResponse.java
│   ├── LoginRequest.java
│   └── LoginResponse.java
├── config/security/
│   ├── SecurityConfig.java
│   ├── JwtTokenProvider.java
│   └── JwtAuthenticationFilter.java
```

## 2. Member 도메인

- `Member` 엔티티: id, email(unique), password(BCrypt encoded), name, role(enum), createdAt
- `Role` enum: ADMIN, USER
- `MemberRepository`: findByEmail(String email)
- 비밀번호는 응답 DTO에 절대 포함하지 않음

## 3. 인증 API

| 엔드포인트 | 설명 | 요청 | 응답 |
|-----------|------|------|------|
| POST /api/v1/auth/signup | 회원가입 | email, password, name | ApiResponse<SignupResponse(id, email, name)> |
| POST /api/v1/auth/login | 로그인 | email, password | ApiResponse<LoginResponse(accessToken, tokenType)> |

## 4. JWT 토큰

- `JwtTokenProvider`: 생성(email+role 클레임, HS256), 검증(서명+만료), 추출(subject)
- 설정: `jwt.secret` (환경변수), `jwt.expiration-ms` (1시간)
- `JwtAuthenticationFilter` (OncePerRequestFilter):
  1. Authorization 헤더에서 Bearer 토큰 추출
  2. JwtTokenProvider로 검증
  3. 유효하면 SecurityContext에 Authentication 설정
  4. 무효하면 다음 필터로 넘김

## 5. Security 설정

| 경로 | 정책 |
|------|------|
| POST /api/v1/auth/** | permitAll |
| GET /swagger-ui/**, /v3/api-docs/** | permitAll |
| POST /api/v1/stations, DELETE /api/v1/stations/** | ADMIN만 |
| 나머지 | 인증 필요 |

- CSRF 비활성화 (REST API)
- 세션: STATELESS
- Swagger UI: 개발 환경에서 permitAll, 프로덕션에서 비활성화

## 6. 에러 처리

추가 ErrorCode:
- DUPLICATE_EMAIL (409)
- INVALID_CREDENTIALS (401)
- UNAUTHORIZED (401)
- FORBIDDEN (403)

인증/인가 실패 응답:
- SecurityConfig 내 AuthenticationEntryPoint(401), AccessDeniedHandler(403)에서 ApiResponse JSON 직접 작성
- 컨트롤러 내 BusinessException은 기존 GlobalExceptionHandler 처리

## 7. 기존 테스트 호환

- StationControllerTest (@WebMvcTest): @WithMockUser 추가
- ChargingFlowIntegrationTest (@SpringBootTest): 회원가입→로그인→토큰 포함 요청
- SessionServiceTest (Mockito): 변경 없음

## 8. 새 테스트

- AuthControllerTest: 회원가입 성공/중복, 로그인 성공/실패
- SecurityIntegrationTest: 401(토큰 없음), 200(유효 토큰), 403(USER→ADMIN API)

## 9. 완료 기준

1. `./gradlew clean test` 전체 통과
2. Swagger에서 signup, login 확인 가능
3. 토큰 없이 요청 시 401
4. 유효 토큰 시 정상 응답
5. USER로 ADMIN API 접근 시 403
