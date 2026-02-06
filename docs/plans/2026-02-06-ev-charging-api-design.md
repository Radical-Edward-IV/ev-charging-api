# EV Charging API - Design Document

**Date:** 2026-02-06
**Status:** Approved
**Purpose:** GS Chargevy Backend Developer Portfolio Project

---

## 1. Overview

EV charging station management REST API.
Seoul metropolitan area charging station data is seeded from the Korea Environment Corporation public API,
and the API provides station search, charger status management, and charging history features.

### Goals
- Demonstrate domain understanding of the EV charging business
- Production-quality code with TDD
- Completable as MVP within 2 days

### Tech Stack
- Java 25, Spring Boot 4.x
- PostgreSQL 17 (Docker container)
- Spring Data JPA
- Swagger/OpenAPI 3.0
- Docker Compose
- Testcontainers (integration tests)

---

## 2. Domain Model

### 2.1 Entities

#### ChargingStation
| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK) | Auto-generated ID |
| stationCode | String | Original station ID from public API (statId) |
| name | String | Station name |
| address | String | Address |
| latitude | Double | Latitude |
| longitude | Double | Longitude |
| operatorName | String | Operating organization name |
| contactNumber | String | Contact number |
| operatingHours | String | Operating hours |
| createdAt | LocalDateTime | Created timestamp |
| updatedAt | LocalDateTime | Updated timestamp |

#### Charger
| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK) | Auto-generated ID |
| station | ChargingStation (FK) | Belongs to station |
| chargerCode | String | Original charger ID from public API (chgerId) |
| type | ChargerType (Enum) | DC_FAST, AC_SLOW, DC_COMBO |
| status | ChargerStatus (Enum) | AVAILABLE, CHARGING, OUT_OF_SERVICE |
| powerKw | BigDecimal | Charging output (kW) |
| connectorType | ConnectorType (Enum) | CCS1, CHADEMO, AC_TYPE_1, AC_TYPE_3 |
| lastStatusChangedAt | LocalDateTime | Last status change timestamp |

#### ChargingSession
| Field | Type | Description |
|-------|------|-------------|
| id | Long (PK) | Auto-generated ID |
| charger | Charger (FK) | Associated charger |
| startTime | LocalDateTime | Charging start time |
| endTime | LocalDateTime | Charging end time |
| energyDeliveredKwh | BigDecimal | Energy delivered (kWh) |
| cost | BigDecimal | Charging cost |
| status | SessionStatus (Enum) | IN_PROGRESS, COMPLETED, FAILED |

### 2.2 Charger State Machine

```
AVAILABLE ──→ CHARGING ──→ AVAILABLE
    │              │
    ↓              ↓
OUT_OF_SERVICE ←───┘
    │
    ↓
AVAILABLE (repair complete)
```

Valid transitions:
- AVAILABLE → CHARGING (start charging)
- AVAILABLE → OUT_OF_SERVICE (breakdown)
- CHARGING → AVAILABLE (charging complete)
- CHARGING → OUT_OF_SERVICE (failure during charging)
- OUT_OF_SERVICE → AVAILABLE (repair complete)

Invalid transitions throw `InvalidStatusTransitionException`.

### 2.3 Enums

**ChargerType:** DC_FAST, AC_SLOW, DC_COMBO

**ChargerStatus:** AVAILABLE, CHARGING, OUT_OF_SERVICE
- Contains `canTransitionTo(ChargerStatus target)` validation method

**ConnectorType:** CCS1, CHADEMO, AC_TYPE_1, AC_TYPE_3

**SessionStatus:** IN_PROGRESS, COMPLETED, FAILED

---

## 3. API Endpoints

### 3.1 Charging Station

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/stations` | Station list (paging) |
| GET | `/api/v1/stations/nearby?lat={}&lng={}&radius={}` | Location-based search (Haversine) |
| GET | `/api/v1/stations/{id}` | Station detail (includes charger list) |
| POST | `/api/v1/stations` | Register station |
| PUT | `/api/v1/stations/{id}` | Update station info |
| DELETE | `/api/v1/stations/{id}` | Delete station |

### 3.2 Charger

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/stations/{stationId}/chargers` | Charger list for station |
| POST | `/api/v1/stations/{stationId}/chargers` | Register charger |
| PATCH | `/api/v1/chargers/{id}/status` | Change charger status (state machine validation) |

### 3.3 Charging Session

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/chargers/{chargerId}/sessions` | Start charging |
| PATCH | `/api/v1/sessions/{id}/complete` | Complete charging |
| GET | `/api/v1/sessions?chargerId={}&startDate={}&endDate={}` | Charging history (filtering) |

### 3.4 Response Format

All responses use unified `ApiResponse<T>` wrapper:

```json
{
  "success": true,
  "data": { ... },
  "error": null
}
```

Error response:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "INVALID_STATUS_TRANSITION",
    "message": "Cannot transition from CHARGING to CHARGING"
  }
}
```

---

## 4. Project Structure

```
src/main/java/com/evcharging/api/
├── EvChargingApiApplication.java
│
├── domain/                          # Domain layer
│   ├── station/
│   │   ├── ChargingStation.java     # Entity
│   │   ├── StationRepository.java   # Repository interface
│   │   └── StationService.java      # Business logic
│   ├── charger/
│   │   ├── Charger.java
│   │   ├── ChargerType.java         # Enum
│   │   ├── ChargerStatus.java       # Enum + state transition validation
│   │   ├── ConnectorType.java       # Enum
│   │   ├── ChargerRepository.java
│   │   └── ChargerService.java
│   └── session/
│       ├── ChargingSession.java
│       ├── SessionStatus.java       # Enum
│       ├── SessionRepository.java
│       └── SessionService.java
│
├── api/                             # API layer (Controller)
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
├── common/                          # Common
│   ├── ApiResponse.java             # Unified response wrapper
│   ├── ErrorCode.java               # Error code Enum
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
│
├── infra/                           # External infrastructure
│   └── openapi/
│       ├── EvChargerApiClient.java  # Public API call client
│       ├── EvChargerApiResponse.java # API response DTO
│       └── DataSeeder.java          # Initial data seeding
│
└── config/                          # Configuration
    └── SwaggerConfig.java           # OpenAPI configuration

src/main/resources/
├── application.yml
├── application-local.yml            # Local development
└── data.sql                         # Fallback sample data

src/test/java/com/evcharging/api/
├── domain/
│   ├── charger/
│   │   └── ChargerStatusTest.java   # State machine unit test
│   └── session/
│       └── SessionServiceTest.java  # Service layer unit test
└── api/
    ├── station/
    │   └── StationControllerTest.java  # @WebMvcTest
    └── integration/
        └── ChargingFlowIntegrationTest.java  # @SpringBootTest + Testcontainers
```

---

## 5. Public API Integration

### 5.1 Korea Environment Corporation API

**Endpoint:** `http://apis.data.go.kr/B552584/EvCharger/getChargerInfo`

**Parameters:**
- `ServiceKey`: Public data portal API key
- `pageNo` / `numOfRows`: Paging
- `zcode=11`: Seoul metropolitan area filter

**Field Mapping:**

| Public API | Domain Model | Description |
|-----------|-------------|-------------|
| statNm | ChargingStation.name | Station name |
| statId | ChargingStation.stationCode | Original station ID |
| addr | ChargingStation.address | Address |
| lat / lng | ChargingStation.latitude/longitude | Coordinates |
| busiNm | ChargingStation.operatorName | Operator name |
| busiCall | ChargingStation.contactNumber | Contact |
| useTime | ChargingStation.operatingHours | Operating hours |
| chgerId | Charger.chargerCode | Original charger ID |
| chgerType | Charger.type | Charger type |
| stat | Charger.status | Charger status |
| output | Charger.powerKw | Output (kW) |

### 5.2 Seeding Strategy

1. `DataSeeder` implements `ApplicationRunner`
2. On startup, check if DB is empty
3. If empty, call public API with `zcode=11` (Seoul)
4. Parse XML response → convert to entities → save to DB
5. On API failure, fall back to built-in `data.sql`
6. API key managed via environment variable: `${OPEN_API_KEY}`

---

## 6. Infrastructure

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
# Build stage
FROM eclipse-temurin:25-jdk AS builder
COPY . .
RUN ./gradlew bootJar

# Runtime stage
FROM eclipse-temurin:25-jre
COPY --from=builder build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 7. Location-Based Search

### Haversine Formula (Native Query)

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

- Input: lat, lng, radius (km)
- Returns stations sorted by distance
- Index on latitude/longitude columns for performance

---

## 8. Testing Strategy

### 8.1 Unit Tests
- **ChargerStatusTest**: Validate all valid/invalid state transitions
- **SessionServiceTest**: Start/complete charging business logic (Mockito)

### 8.2 API Tests
- **StationControllerTest**: `@WebMvcTest` - endpoint validation, request/response format

### 8.3 Integration Tests
- **ChargingFlowIntegrationTest**: `@SpringBootTest` + Testcontainers (PostgreSQL)
  - Full charging flow: station lookup → start charging → complete → verify history

---

## 9. Error Handling

### ErrorCode Enum

| Code | HTTP Status | Description |
|------|------------|-------------|
| STATION_NOT_FOUND | 404 | Station not found |
| CHARGER_NOT_FOUND | 404 | Charger not found |
| SESSION_NOT_FOUND | 404 | Session not found |
| INVALID_STATUS_TRANSITION | 400 | Invalid charger status transition |
| CHARGER_NOT_AVAILABLE | 409 | Charger not available for charging |
| SESSION_ALREADY_COMPLETED | 409 | Session already completed |

### GlobalExceptionHandler
- Catches domain exceptions and maps to appropriate HTTP status codes
- Returns unified `ApiResponse` error format

---

## 10. Data Sources

- [Korea Environment Corporation EV Charger Info API](https://www.data.go.kr/data/15076352/openapi.do)
- [Public Data Portal API Usage Guide](https://www.dinolabs.ai/383)
