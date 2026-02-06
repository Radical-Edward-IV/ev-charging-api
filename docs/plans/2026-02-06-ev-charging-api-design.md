# EV 충전소 관리 API - 설계 문서

**작성일:** 2026-02-06  
**상태:** 승인됨  
**목적:** 백엔드 개발자 포트폴리오 프로젝트

---

## 1. 개요

EV 충전소 관리 REST API.
한국환경공단 공공 API에서 서울 수도권 충전소 데이터를 시딩하고,
충전소 검색, 충전기 상태 관리, 충전 이력 기능을 제공한다.

### 목표
- EV 충전 비즈니스 도메인 이해도 시연
- TDD 적용한 프로덕션 수준 코드 품질
- 2일 내 완성 가능한 MVP

### 기술 스택
- Java 25, Spring Boot 4.x
- PostgreSQL 17 (Docker 컨테이너)
- Spring Data JPA
- Swagger/OpenAPI 3.0
- Docker Compose
- Testcontainers (통합 테스트)

### 개발 환경
- **Dev Container** 사용: 로컬에 JDK/PostgreSQL을 설치하지 않고, 컨테이너 기반으로 동일한 개발 환경을 보장한다. 프로젝트 루트의 `.devcontainer/` 설정으로 에디터에서 컨테이너 내부에서 개발·실행·테스트를 진행한다.

---

## 2. 도메인 모델

### 2.1 엔티티

#### ChargingStation (충전소)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 생성 ID |
| stationCode | String | 공공 API 원본 충전소 ID (statId) |
| name | String | 충전소명 |
| address | String | 주소 |
| latitude | Double | 위도 |
| longitude | Double | 경도 |
| operatorName | String | 운영기관명 |
| contactNumber | String | 연락처 |
| operatingHours | String | 이용가능시간 |
| createdAt | LocalDateTime | 생성 일시 |
| updatedAt | LocalDateTime | 수정 일시 |

#### Charger (충전기)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 생성 ID |
| station | ChargingStation (FK) | 소속 충전소 |
| chargerCode | String | 공공 API 원본 충전기 ID (chgerId) |
| type | ChargerType (Enum) | DC_FAST, AC_SLOW, DC_COMBO |
| status | ChargerStatus (Enum) | AVAILABLE, CHARGING, OUT_OF_SERVICE |
| powerKw | BigDecimal | 충전 출력 (kW) |
| connectorType | ConnectorType (Enum) | CCS1, CHADEMO, AC_TYPE_1, AC_TYPE_3 |
| lastStatusChangedAt | LocalDateTime | 마지막 상태 변경 일시 |

#### ChargingSession (충전 세션)
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long (PK) | 자동 생성 ID |
| charger | Charger (FK) | 연결된 충전기 |
| startTime | LocalDateTime | 충전 시작 시간 |
| endTime | LocalDateTime | 충전 종료 시간 |
| energyDeliveredKwh | BigDecimal | 충전량 (kWh) |
| cost | BigDecimal | 충전 요금 |
| status | SessionStatus (Enum) | IN_PROGRESS, COMPLETED, FAILED |

### 2.2 충전기 상태 머신

```
AVAILABLE ──→ CHARGING ──→ AVAILABLE
    │              │
    ↓              ↓
OUT_OF_SERVICE ←───┘
    │
    ↓
AVAILABLE (수리 완료)
```

유효한 전이:
- AVAILABLE → CHARGING (충전 시작)
- AVAILABLE → OUT_OF_SERVICE (고장 발생)
- CHARGING → AVAILABLE (충전 완료)
- CHARGING → OUT_OF_SERVICE (충전 중 고장)
- OUT_OF_SERVICE → AVAILABLE (수리 완료)

유효하지 않은 전이 시 `InvalidStatusTransitionException` 발생.

### 2.3 Enum 정의

**ChargerType:** DC_FAST, AC_SLOW, DC_COMBO

**ChargerStatus:** AVAILABLE, CHARGING, OUT_OF_SERVICE
- `canTransitionTo(ChargerStatus target)` 상태 전이 검증 메서드 포함

**ConnectorType:** CCS1, CHADEMO, AC_TYPE_1, AC_TYPE_3

**SessionStatus:** IN_PROGRESS, COMPLETED, FAILED

---

## 3. API 엔드포인트

### 3.1 충전소 (ChargingStation)

| Method | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/stations` | 충전소 전체 목록 (페이징) |
| GET | `/api/v1/stations/nearby?lat={}&lng={}&radius={}` | 위치 기반 검색 (Haversine) |
| GET | `/api/v1/stations/{id}` | 충전소 상세 조회 (충전기 목록 포함) |
| POST | `/api/v1/stations` | 충전소 등록 |
| PUT | `/api/v1/stations/{id}` | 충전소 정보 수정 |
| DELETE | `/api/v1/stations/{id}` | 충전소 삭제 |

### 3.2 충전기 (Charger)

| Method | 엔드포인트 | 설명 |
|--------|-----------|------|
| GET | `/api/v1/stations/{stationId}/chargers` | 충전소의 충전기 목록 |
| POST | `/api/v1/stations/{stationId}/chargers` | 충전기 등록 |
| PATCH | `/api/v1/chargers/{id}/status` | 충전기 상태 변경 (상태머신 검증) |

### 3.3 충전 세션 (ChargingSession)

| Method | 엔드포인트 | 설명 |
|--------|-----------|------|
| POST | `/api/v1/chargers/{chargerId}/sessions` | 충전 시작 |
| PATCH | `/api/v1/sessions/{id}/complete` | 충전 완료 |
| GET | `/api/v1/sessions?chargerId={}&startDate={}&endDate={}` | 충전 이력 조회 (필터링) |

### 3.4 응답 형식

모든 응답은 통일된 `ApiResponse<T>` wrapper 사용:

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

에러 응답:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "CHARGING에서 CHARGING으로 전이할 수 없습니다"
  }
}
```

---

## 4. 프로젝트 구조

```
src/main/java/com/evcharging/api/
├── EvChargingApiApplication.java
│
├── domain/                          # 도메인 계층
│   ├── station/
│   │   ├── ChargingStation.java     # 엔티티
│   │   ├── StationRepository.java   # Repository 인터페이스
│   │   └── StationService.java      # 비즈니스 로직
│   ├── charger/
│   │   ├── Charger.java
│   │   ├── ChargerType.java         # Enum
│   │   ├── ChargerStatus.java       # Enum + 상태 전이 검증
│   │   ├── ConnectorType.java       # Enum
│   │   ├── ChargerRepository.java
│   │   └── ChargerService.java
│   └── session/
│       ├── ChargingSession.java
│       ├── SessionStatus.java       # Enum
│       ├── SessionRepository.java
│       └── SessionService.java
│
├── api/                             # API 계층 (Controller)
│   ├── station/
│   │   ├── StationController.java
│   │   ├── StationRequest.java      # DTO
│   │   └── StationResponse.java     # DTO
│   ├── charger/
│   │   ├── ChargerController.java
│   │   ├── ChargerStatusRequest.java
│   │   └── ChargerResponse.java
│   └── session/
│       ├── SessionController.java
│       ├── SessionStartRequest.java
│       └── SessionResponse.java
│
├── common/                          # 공통
│   ├── ApiResponse.java             # 통일된 응답 wrapper
│   ├── ErrorCode.java               # 에러 코드 Enum
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│
├── infra/                           # 외부 인프라 연동
│   └── openapi/
│       ├── EvChargerApiClient.java  # 공공 API 호출 클라이언트
│       ├── EvChargerApiResponse.java # API 응답 DTO
│       └── DataSeeder.java          # 초기 데이터 시딩
│
└── config/                          # 설정
    └── SwaggerConfig.java           # OpenAPI 설정

src/main/resources/
├── application.yml
├── application-local.yml            # 로컬 개발용
└── data.sql                         # 폴백 샘플 데이터

src/test/java/com/evcharging/api/
├── domain/
│   ├── charger/
│   │   └── ChargerStatusTest.java   # 상태 머신 단위 테스트
│   └── session/
│       └── SessionServiceTest.java  # 서비스 계층 단위 테스트
└── api/
    ├── station/
    │   └── StationControllerTest.java  # @WebMvcTest
    └── integration/
        └── ChargingFlowIntegrationTest.java  # @SpringBootTest + Testcontainers
```

---

## 5. 공공 API 연동

### 5.1 한국환경공단 API

**엔드포인트:** `http://apis.data.go.kr/B552584/EvCharger/getChargerInfo`

**요청 파라미터:**
- `ServiceKey`: 공공데이터포털 API 인증키
- `pageNo` / `numOfRows`: 페이징
- `zcode=11`: 서울 수도권 필터

**필드 매핑:**

| 공공 API 필드 | 도메인 모델 | 설명 |
|--------------|-----------|------|
| statNm | ChargingStation.name | 충전소명 |
| statId | ChargingStation.stationCode | 원본 충전소 ID |
| addr | ChargingStation.address | 주소 |
| lat / lng | ChargingStation.latitude/longitude | 위도/경도 |
| busiNm | ChargingStation.operatorName | 운영기관명 |
| busiCall | ChargingStation.contactNumber | 연락처 |
| useTime | ChargingStation.operatingHours | 이용가능시간 |
| chgerId | Charger.chargerCode | 원본 충전기 ID |
| chgerType | Charger.type | 충전기 타입 |
| stat | Charger.status | 충전기 상태 |
| output | Charger.powerKw | 충전 출력 (kW) |

### 5.2 시딩 전략

1. `DataSeeder`가 `ApplicationRunner` 구현
2. 기동 시 DB가 비어있는지 확인
3. 비어있으면 공공 API 호출 (`zcode=11`, 서울)
4. XML 응답 파싱 → 엔티티 변환 → DB 저장
5. API 호출 실패 시 내장 `data.sql`로 폴백
6. API 키는 환경 변수로 관리: `${OPEN_API_KEY}`

---

## 6. 인프라

### 6.1 Docker Compose

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/evcharging
      OPEN_API_KEY: ${OPEN_API_KEY}
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:17
    ports: ["5432:5432"]
    environment:
      POSTGRES_DB: evcharging
      POSTGRES_USER: evuser
      POSTGRES_PASSWORD: evpass
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U evuser -d evcharging"]
      interval: 5s
      retries: 5
```

### 6.2 Dockerfile (Multi-stage)

```dockerfile
# 빌드 단계
FROM eclipse-temurin:25-jdk AS builder
COPY . .
RUN ./gradlew bootJar

# 실행 단계
FROM eclipse-temurin:25-jre
COPY --from=builder build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 7. 위치 기반 검색

### Haversine 공식 (Native Query)

```sql
SELECT s.*, (
  6371 * acos(
    cos(radians(:lat)) * cos(radians(s.latitude))
    * cos(radians(s.longitude) - radians(:lng))
    + sin(radians(:lat)) * sin(radians(s.latitude))
  )
) AS distance
FROM charging_station s
HAVING distance < :radius
ORDER BY distance
```

- 입력: lat, lng, radius (km)
- 거리순 정렬된 충전소 반환
- latitude/longitude 컬럼에 인덱스 설정으로 성능 최적화

---

## 8. 테스트 전략

### 8.1 단위 테스트
- **ChargerStatusTest**: 모든 유효/무효 상태 전이 검증
- **SessionServiceTest**: 충전 시작/완료 비즈니스 로직 (Mockito)

### 8.2 API 테스트
- **StationControllerTest**: `@WebMvcTest` - 엔드포인트 검증, 요청/응답 형식

### 8.3 통합 테스트
- **ChargingFlowIntegrationTest**: `@SpringBootTest` + Testcontainers (PostgreSQL)
  - 전체 충전 플로우: 충전소 조회 → 충전 시작 → 충전 완료 → 이력 확인

---

## 9. 에러 처리

### ErrorCode Enum

| 코드 | HTTP 상태 | 설명 |
|------|----------|------|
| STATION_NOT_FOUND | 404 | 충전소를 찾을 수 없음 |
| CHARGER_NOT_FOUND | 404 | 충전기를 찾을 수 없음 |
| SESSION_NOT_FOUND | 404 | 충전 세션을 찾을 수 없음 |
| INVALID_STATUS_TRANSITION | 400 | 유효하지 않은 충전기 상태 전이 |
| CHARGER_NOT_AVAILABLE | 409 | 충전기가 사용 가능 상태가 아님 |
| SESSION_ALREADY_COMPLETED | 409 | 이미 완료된 충전 세션 |

### GlobalExceptionHandler
- 도메인 예외를 잡아서 적절한 HTTP 상태 코드로 매핑
- 통일된 `ApiResponse` 에러 형식 반환

---

## 10. 참고 자료

- [한국환경공단_전기자동차 충전소 정보 API](https://www.data.go.kr/data/15076352/openapi.do)
- [공공데이터포털 API 활용 가이드](https://www.dinolabs.ai/383)
