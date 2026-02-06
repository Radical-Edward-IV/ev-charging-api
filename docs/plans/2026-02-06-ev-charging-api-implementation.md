# EV 충전소 관리 API 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** 한국환경공단 공공 API 기반의 EV 충전소 관리 REST API MVP 구현

**아키텍처:** Dev Container 기반 개발 환경에서, 도메인 중심 패키지 구조(station/charger/session)로 Spring Boot 4.x REST API를 구현한다. 도메인 계층에서 비즈니스 로직(상태머신 등)을 캡슐화하고, API 계층에서 HTTP 관심사만 처리한다. 공공 API 데이터 시딩은 infra 패키지에서 분리하여 처리한다.

**기술 스택:** Java 25, Spring Boot 4.0.2, PostgreSQL 17, Spring Data JPA, Gradle 9.x, Testcontainers, SpringDoc OpenAPI 3.0.1, Docker Compose, Dev Container

**설계 문서:** `docs/plans/2026-02-06-ev-charging-api-design.md`

---

## Task 1: Dev Container + 프로젝트 초기화

**파일:**
- 생성: `.devcontainer/devcontainer.json`
- 생성: `.devcontainer/docker-compose.yml`
- 생성: `build.gradle`
- 생성: `settings.gradle`
- 생성: `src/main/java/com/evcharging/api/EvChargingApiApplication.java`
- 생성: `src/main/resources/application.yml`
- 생성: `src/main/resources/application-local.yml`
- 생성: `.gitignore`

**Step 1: .devcontainer/devcontainer.json 작성**

```json
{
  // Display name of the Dev Container in VS Code/Cursor
  "name": "EV Charging API",

  // Use docker-compose for multi-container setup
  "dockerComposeFile": "docker-compose.yml",
  "service": "app",
  "workspaceFolder": "/workspace",

  // Features: Java 25 + Gradle, Docker-in-Docker
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "25",
      "installGradle": "true",
      "gradleVersion": "9.3.1"
    },
    "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  },

  // List of ports to forward from the container
  "forwardPorts": [
    8080,  // Spring Boot application
    5432   // PostgreSQL
  ],

  // Default user inside the container
  "remoteUser": "vscode",

  // VS Code/Cursor-specific settings and extensions
  "customizations": {
    "vscode": {
      // Default user settings for VS Code/Cursor inside the container
      "settings": {
        "java.configuration.updateBuildConfiguration": "interactive",
        "java.compile.nullAnalysis.mode": "automatic",
        "[java]": {
          "editor.formatOnSave": true
        }
      },

      // Recommended VS Code/Cursor extensions
      "extensions": [
        "vscjava.vscode-java-pack",
        "vmware.vscode-boot-dev-pack",
        "vscjava.vscode-gradle",
        "vscodevim.vim",
        "christian-kohler.path-intellisense",
        "pkief.material-icon-theme",
        "esbenp.prettier-vscode"
      ]
    }
  },

  // Command executed after container is created
  "postCreateCommand": "java -version && gradle --version"
}
```

**Step 2: .devcontainer/docker-compose.yml 작성**

```yaml
services:
  app:
    image: mcr.microsoft.com/devcontainers/base:ubuntu
    volumes:
      - ..:/workspace:cached
    command: sleep infinity
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:17
    restart: unless-stopped
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: evcharging
      POSTGRES_USER: evuser
      POSTGRES_PASSWORD: evpass
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U evuser -d evcharging"]
      interval: 5s
      retries: 5

volumes:
  pgdata:
```

**Step 3: build.gradle 작성**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.2'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.evcharging'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.1'

    runtimeOnly 'org.postgresql:postgresql'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:postgresql'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

**Step 4: settings.gradle 작성**

```groovy
rootProject.name = 'ev-charging-api'
```

**Step 5: EvChargingApiApplication.java 작성**

```java
package com.evcharging.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EvChargingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvChargingApiApplication.class, args);
    }
}
```

**Step 6: application.yml 작성**

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/evcharging}
    username: ${SPRING_DATASOURCE_USERNAME:evuser}
    password: ${SPRING_DATASOURCE_PASSWORD:evpass}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  sql:
    init:
      mode: never

server:
  port: 8080

openapi:
  service-key: ${OPEN_API_KEY:}
```

**Step 7: application-local.yml 작성**

Dev Container 내에서 개발 시 사용하는 프로필. `postgres` 호스트는 docker-compose 서비스명으로 접근.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/evcharging
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
  sql:
    init:
      mode: always

logging:
  level:
    com.evcharging: DEBUG
    org.hibernate.SQL: DEBUG
```

**Step 8: .gitignore 작성**

```
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
*.class
*.jar
*.war
.idea/
*.iml
.vscode/
.env
```

**Step 9: Gradle Wrapper 생성 및 빌드 확인**

```bash
gradle wrapper --gradle-version 9.3.1
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 10: 커밋**

```bash
git add .devcontainer/ build.gradle settings.gradle .gitignore gradle/ gradlew gradlew.bat \
  src/main/java/com/evcharging/api/EvChargingApiApplication.java \
  src/main/resources/application.yml src/main/resources/application-local.yml
git commit -m "chore: initialize project with Dev Container and Spring Boot 4.0.2"
```

---

## Task 2: 프로덕션 Docker Compose + Dockerfile

**파일:**
- 생성: `docker-compose.yml` (프로젝트 루트, 프로덕션 배포용)
- 생성: `Dockerfile`

**Step 1: docker-compose.yml 작성 (프로덕션 배포용)**

Dev Container의 `.devcontainer/docker-compose.yml`과 별도. 이 파일은 빌드된 앱을 배포할 때 사용.

```yaml
services:
  postgres:
    image: postgres:17
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: evcharging
      POSTGRES_USER: evuser
      POSTGRES_PASSWORD: evpass
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U evuser -d evcharging"]
      interval: 5s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/evcharging
      SPRING_DATASOURCE_USERNAME: evuser
      SPRING_DATASOURCE_PASSWORD: evpass
      SPRING_PROFILES_ACTIVE: local
      OPEN_API_KEY: ${OPEN_API_KEY:-}
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  pgdata:
```

**Step 2: Dockerfile 작성 (Multi-stage)**

```dockerfile
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Step 3: 커밋**

```bash
git add docker-compose.yml Dockerfile
git commit -m "infra: add production Docker Compose and multi-stage Dockerfile"
```

---

## Task 3: 공통 모듈 (ApiResponse, ErrorCode, GlobalExceptionHandler)

**파일:**
- 생성: `src/main/java/com/evcharging/api/common/ApiResponse.java`
- 생성: `src/main/java/com/evcharging/api/common/ErrorCode.java`
- 생성: `src/main/java/com/evcharging/api/common/GlobalExceptionHandler.java`
- 생성: `src/main/java/com/evcharging/api/common/BusinessException.java`
- 테스트: `src/test/java/com/evcharging/api/common/ApiResponseTest.java`

**Step 1: 실패하는 테스트 작성**

```java
package com.evcharging.api.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void success_응답을_생성한다() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("hello");
        assertThat(response.error()).isNull();
    }

    @Test
    void error_응답을_생성한다() {
        ApiResponse<Void> response = ApiResponse.error("NOT_FOUND", "찾을 수 없습니다");

        assertThat(response.success()).isFalse();
        assertThat(response.data()).isNull();
        assertThat(response.error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("찾을 수 없습니다");
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew test --tests "com.evcharging.api.common.ApiResponseTest" --info
```

기대 결과: 컴파일 에러 (ApiResponse 클래스 없음)

**Step 3: ApiResponse 구현**

```java
package com.evcharging.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorDetail error
) {
    public record ErrorDetail(String code, String message) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(code, message));
    }
}
```

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.evcharging.api.common.ApiResponseTest" --info
```

기대 결과: 2 tests PASSED

**Step 5: ErrorCode, BusinessException, GlobalExceptionHandler 구현**

`ErrorCode.java`:

```java
package com.evcharging.api.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    STATION_NOT_FOUND(HttpStatus.NOT_FOUND, "충전소를 찾을 수 없습니다"),
    CHARGER_NOT_FOUND(HttpStatus.NOT_FOUND, "충전기를 찾을 수 없습니다"),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "충전 세션을 찾을 수 없습니다"),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "유효하지 않은 상태 전이입니다"),
    CHARGER_NOT_AVAILABLE(HttpStatus.CONFLICT, "충전기가 사용 가능 상태가 아닙니다"),
    SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 충전 세션입니다");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() { return httpStatus; }
    public String getMessage() { return message; }
}
```

`BusinessException.java`:

```java
package com.evcharging.api.common;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() { return errorCode; }
}
```

`GlobalExceptionHandler.java`:

```java
package com.evcharging.api.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.name(), e.getMessage()));
    }
}
```

**Step 6: 전체 테스트 통과 확인**

```bash
./gradlew test --info
```

기대 결과: BUILD SUCCESSFUL

**Step 7: 커밋**

```bash
git add src/main/java/com/evcharging/api/common/ \
  src/test/java/com/evcharging/api/common/
git commit -m "feat: add common module (ApiResponse, ErrorCode, GlobalExceptionHandler)"
```

---

## Task 4: 충전기 상태 머신 (ChargerStatus Enum + 테스트)

**파일:**
- 생성: `src/main/java/com/evcharging/api/domain/charger/ChargerStatus.java`
- 생성: `src/main/java/com/evcharging/api/domain/charger/ChargerType.java`
- 생성: `src/main/java/com/evcharging/api/domain/charger/ConnectorType.java`
- 생성: `src/main/java/com/evcharging/api/domain/session/SessionStatus.java`
- 테스트: `src/test/java/com/evcharging/api/domain/charger/ChargerStatusTest.java`

**Step 1: 실패하는 테스트 작성**

```java
package com.evcharging.api.domain.charger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChargerStatusTest {

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, CHARGING",
            "AVAILABLE, OUT_OF_SERVICE",
            "CHARGING, AVAILABLE",
            "CHARGING, OUT_OF_SERVICE",
            "OUT_OF_SERVICE, AVAILABLE"
    })
    void 유효한_상태_전이를_허용한다(ChargerStatus from, ChargerStatus to) {
        assertThat(from.canTransitionTo(to)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "AVAILABLE, AVAILABLE",
            "CHARGING, CHARGING",
            "OUT_OF_SERVICE, OUT_OF_SERVICE",
            "OUT_OF_SERVICE, CHARGING"
    })
    void 유효하지_않은_상태_전이를_거부한다(ChargerStatus from, ChargerStatus to) {
        assertThat(from.canTransitionTo(to)).isFalse();
    }

    @Test
    void 유효하지_않은_전이_시_예외를_던진다() {
        assertThatThrownBy(() ->
                ChargerStatus.OUT_OF_SERVICE.validateTransition(ChargerStatus.CHARGING)
        ).isInstanceOf(IllegalStateException.class);
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew test --tests "com.evcharging.api.domain.charger.ChargerStatusTest" --info
```

기대 결과: 컴파일 에러 (ChargerStatus 없음)

**Step 3: ChargerStatus 구현**

```java
package com.evcharging.api.domain.charger;

import java.util.Set;

public enum ChargerStatus {

    AVAILABLE(Set.of("CHARGING", "OUT_OF_SERVICE")),
    CHARGING(Set.of("AVAILABLE", "OUT_OF_SERVICE")),
    OUT_OF_SERVICE(Set.of("AVAILABLE"));

    private final Set<String> allowedTransitions;

    ChargerStatus(Set<String> allowedTransitions) {
        this.allowedTransitions = allowedTransitions;
    }

    public boolean canTransitionTo(ChargerStatus target) {
        return allowedTransitions.contains(target.name());
    }

    public void validateTransition(ChargerStatus target) {
        if (!canTransitionTo(target)) {
            throw new IllegalStateException(
                    "%s에서 %s(으)로 전이할 수 없습니다".formatted(this.name(), target.name())
            );
        }
    }
}
```

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.evcharging.api.domain.charger.ChargerStatusTest" --info
```

기대 결과: 3 tests PASSED

**Step 5: 나머지 Enum 작성**

`ChargerType.java`:

```java
package com.evcharging.api.domain.charger;

public enum ChargerType {
    DC_FAST,
    AC_SLOW,
    DC_COMBO
}
```

`ConnectorType.java`:

```java
package com.evcharging.api.domain.charger;

public enum ConnectorType {
    CCS1,
    CHADEMO,
    AC_TYPE_1,
    AC_TYPE_3
}
```

`SessionStatus.java`:

```java
package com.evcharging.api.domain.session;

public enum SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
```

**Step 6: 전체 테스트 통과 확인**

```bash
./gradlew test --info
```

기대 결과: BUILD SUCCESSFUL

**Step 7: 커밋**

```bash
git add src/main/java/com/evcharging/api/domain/charger/ \
  src/main/java/com/evcharging/api/domain/session/SessionStatus.java \
  src/test/java/com/evcharging/api/domain/charger/
git commit -m "feat: add charger state machine with transition validation"
```

---

## Task 5: JPA 엔티티 (ChargingStation, Charger, ChargingSession)

**파일:**
- 생성: `src/main/java/com/evcharging/api/domain/station/ChargingStation.java`
- 생성: `src/main/java/com/evcharging/api/domain/charger/Charger.java`
- 생성: `src/main/java/com/evcharging/api/domain/session/ChargingSession.java`

**Step 1: ChargingStation 엔티티 작성**

설계 문서 2.1절 참조. 필드: id, stationCode, name, address, latitude, longitude, operatorName, contactNumber, operatingHours, createdAt, updatedAt. OneToMany → Charger. @Index on (latitude, longitude). @PrePersist/@PreUpdate for timestamps. update() 메서드, addCharger() 메서드.

**Step 2: Charger 엔티티 작성**

설계 문서 2.1절 참조. 필드: id, chargerCode, type(Enum), status(Enum), powerKw, connectorType(Enum), lastStatusChangedAt. ManyToOne → ChargingStation. OneToMany → ChargingSession. changeStatus()에서 validateTransition() 호출.

**Step 3: ChargingSession 엔티티 작성**

설계 문서 2.1절 참조. 필드: id, charger(FK), startTime, endTime, energyDeliveredKwh, cost, status(Enum). 팩토리 메서드 start(Charger). complete(energyDeliveredKwh, cost)에서 상태 검증.

**Step 4: 빌드 확인**

```bash
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 5: 커밋**

```bash
git add src/main/java/com/evcharging/api/domain/
git commit -m "feat: add JPA entities (ChargingStation, Charger, ChargingSession)"
```

---

## Task 6: Repository 계층

**파일:**
- 생성: `src/main/java/com/evcharging/api/domain/station/StationRepository.java`
- 생성: `src/main/java/com/evcharging/api/domain/charger/ChargerRepository.java`
- 생성: `src/main/java/com/evcharging/api/domain/session/SessionRepository.java`

**Step 1: StationRepository 작성 (Haversine 쿼리 포함)**

Haversine 공식으로 위치 기반 검색. native query 사용. WHERE절에서 거리 필터링 (HAVING 아님). @Param으로 lat, lng, radius 바인딩.

**Step 2: ChargerRepository 작성**

`findByStationId(Long stationId)` 메서드.

**Step 3: SessionRepository 작성**

`findByChargerIdAndStartTimeBetween()`, `findByChargerId()` 메서드.

**Step 4: 빌드 확인**

```bash
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 5: 커밋**

```bash
git add src/main/java/com/evcharging/api/domain/
git commit -m "feat: add repository interfaces with Haversine proximity query"
```

---

## Task 7: Service 계층 + 단위 테스트

**파일:**
- 생성: `src/main/java/com/evcharging/api/domain/station/StationService.java`
- 생성: `src/main/java/com/evcharging/api/domain/charger/ChargerService.java`
- 생성: `src/main/java/com/evcharging/api/domain/session/SessionService.java`
- 테스트: `src/test/java/com/evcharging/api/domain/session/SessionServiceTest.java`

**Step 1: SessionService 실패하는 테스트 작성**

3개 테스트:
- `사용가능한_충전기로_충전을_시작한다()`: Charger(AVAILABLE) → startCharging → session.status == IN_PROGRESS, charger.status == CHARGING
- `사용불가능한_충전기로_충전시작_시_예외를_던진다()`: Charger(OUT_OF_SERVICE) → BusinessException
- `진행중인_세션을_완료한다()`: session.complete() → status == COMPLETED, charger.status == AVAILABLE

Mockito 사용: @Mock SessionRepository, ChargerRepository. @InjectMocks SessionService.

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew test --tests "com.evcharging.api.domain.session.SessionServiceTest" --info
```

기대 결과: 컴파일 에러 (SessionService 없음)

**Step 3: SessionService 구현**

startCharging(Long chargerId): charger 조회 → AVAILABLE 검증 → CHARGING 전이 → session 생성 → 저장.
completeCharging(Long sessionId, BigDecimal energyKwh, BigDecimal cost): session 조회 → complete() → charger AVAILABLE 전이.
findByCharger(Long chargerId, LocalDateTime start, LocalDateTime end): 날짜 필터 있으면 범위 검색, 없으면 전체.

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.evcharging.api.domain.session.SessionServiceTest" --info
```

기대 결과: 3 tests PASSED

**Step 5: StationService 구현**

findAll(Pageable), findById(Long), findNearby(lat, lng, radiusKm), create(ChargingStation), update(Long, ...), delete(Long).

**Step 6: ChargerService 구현**

findByStation(Long stationId), create(Long stationId, Charger), changeStatus(Long chargerId, ChargerStatus).

**Step 7: 전체 테스트 통과 확인**

```bash
./gradlew test --info
```

기대 결과: BUILD SUCCESSFUL

**Step 8: 커밋**

```bash
git add src/main/java/com/evcharging/api/domain/ \
  src/test/java/com/evcharging/api/domain/
git commit -m "feat: add service layer with charging session lifecycle logic"
```

---

## Task 8: DTO + Controller (충전소)

**파일:**
- 생성: `src/main/java/com/evcharging/api/api/station/StationRequest.java`
- 생성: `src/main/java/com/evcharging/api/api/station/StationResponse.java`
- 생성: `src/main/java/com/evcharging/api/api/station/StationController.java`
- 생성: `src/main/java/com/evcharging/api/api/charger/ChargerResponse.java`
- 테스트: `src/test/java/com/evcharging/api/api/station/StationControllerTest.java`

**Step 1: 실패하는 테스트 작성**

@WebMvcTest(StationController.class). @MockitoBean StationService. GET /api/v1/stations/1 → 200 OK, $.success == true, $.data.name == "강남역 충전소".

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew test --tests "com.evcharging.api.api.station.StationControllerTest" --info
```

기대 결과: 컴파일 에러

**Step 3: DTO 구현**

StationRequest: record(stationCode, @NotBlank name, @NotBlank address, latitude, longitude, operatorName, contactNumber, operatingHours).
StationResponse: record(..., List<ChargerResponse> chargers). static from(ChargingStation).
ChargerResponse: record(id, chargerCode, type, status, powerKw, connectorType). static from(Charger).

**Step 4: StationController 구현**

설계 문서 3.1절의 6개 엔드포인트. @RequestMapping("/api/v1/stations"). 모든 응답 ApiResponse<T> wrapper.

**Step 5: 테스트 통과 확인**

```bash
./gradlew test --tests "com.evcharging.api.api.station.StationControllerTest" --info
```

기대 결과: 1 test PASSED

**Step 6: 커밋**

```bash
git add src/main/java/com/evcharging/api/api/ \
  src/test/java/com/evcharging/api/api/
git commit -m "feat: add station REST controller with DTO layer"
```

---

## Task 9: Controller (충전기 + 세션)

**파일:**
- 생성: `src/main/java/com/evcharging/api/api/charger/ChargerController.java`
- 생성: `src/main/java/com/evcharging/api/api/charger/ChargerStatusRequest.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionController.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionCompleteRequest.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionResponse.java`

**Step 1: DTO 작성**

ChargerStatusRequest: record(@NotNull ChargerStatus status).
CreateChargerRequest: record(chargerCode, type, powerKw, connectorType). ChargerController 내부 record.
SessionCompleteRequest: record(@NotNull @Positive energyDeliveredKwh, @NotNull @Positive cost).
SessionResponse: record(id, chargerId, startTime, endTime, energyDeliveredKwh, cost, status). static from(ChargingSession).

**Step 2: ChargerController 구현**

설계 문서 3.2절의 3개 엔드포인트.

**Step 3: SessionController 구현**

설계 문서 3.3절의 3개 엔드포인트. @DateTimeFormat(iso = ISO.DATE_TIME) for startDate/endDate.

**Step 4: 빌드 확인**

```bash
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 5: 커밋**

```bash
git add src/main/java/com/evcharging/api/api/
git commit -m "feat: add charger and session REST controllers"
```

---

## Task 10: Swagger/OpenAPI 설정

**파일:**
- 생성: `src/main/java/com/evcharging/api/config/SwaggerConfig.java`

**Step 1: SwaggerConfig 작성**

@Configuration. @Bean OpenAPI. title: "EV 충전소 관리 API". description. version: "v1.0.0". contact: "Kim Kwanghyeok".

**Step 2: 빌드 확인**

```bash
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 3: 커밋**

```bash
git add src/main/java/com/evcharging/api/config/
git commit -m "feat: add Swagger/OpenAPI configuration"
```

---

## Task 11: 공공 API 데이터 시딩

**파일:**
- 생성: `src/main/java/com/evcharging/api/infra/openapi/EvChargerApiClient.java`
- 생성: `src/main/java/com/evcharging/api/infra/openapi/EvChargerApiResponse.java`
- 생성: `src/main/java/com/evcharging/api/infra/openapi/DataSeeder.java`
- 생성: `src/main/resources/data.sql`

**Step 1: EvChargerApiResponse 작성**

설계 문서 5.1절 필드 매핑 참조. 중첩 record: Response(Header, Body), Body(Items, totalCount, pageNo, numOfRows), Items(List<Item>), Item(statNm, statId, chgerId, chgerType, addr, lat, lng, busiNm, busiCall, useTime, stat, output).

**Step 2: EvChargerApiClient 작성**

RestClient 사용. BASE_URL: "http://apis.data.go.kr/B552584/EvCharger". fetchSeoulChargers(pageNo, numOfRows): serviceKey, zcode=11, dataType=JSON. 실패 시 빈 리스트 반환.

**Step 3: DataSeeder 작성**

ApplicationRunner 구현. 설계 문서 5.2절 시딩 전략 참조. DB 비어있을 때만 실행. 공공 API 호출 → statId 기준으로 ChargingStation 그룹핑 → Charger 추가 → saveAll. chgerType 코드 → ChargerType/ConnectorType 매핑.

**Step 4: data.sql 폴백 데이터 작성**

서울시 5개 충전소, 10개 충전기. spring.sql.init.mode=always일 때만 실행됨.

**Step 5: 빌드 확인**

```bash
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 6: 커밋**

```bash
git add src/main/java/com/evcharging/api/infra/ \
  src/main/resources/data.sql
git commit -m "feat: add public API data seeder with fallback SQL data"
```

---

## Task 12: 통합 테스트 (Testcontainers)

**파일:**
- 생성: `src/test/java/com/evcharging/api/api/integration/ChargingFlowIntegrationTest.java`

**Step 1: 통합 테스트 작성**

@SpringBootTest + @AutoConfigureMockMvc + @Testcontainers. @Container @ServiceConnection PostgreSQLContainer("postgres:17"). DynamicPropertySource: ddl-auto=create-drop, sql.init.mode=never, openapi.service-key="".

전체 플로우 테스트:
1. POST /api/v1/stations → 충전소 등록
2. POST /api/v1/stations/{id}/chargers → 충전기 등록
3. POST /api/v1/chargers/{id}/sessions → 충전 시작 (status=IN_PROGRESS)
4. GET /api/v1/stations/{id}/chargers → 충전기 상태 CHARGING 확인
5. PATCH /api/v1/sessions/{id}/complete → 충전 완료 (status=COMPLETED)
6. GET /api/v1/stations/{id}/chargers → 충전기 상태 AVAILABLE 복귀 확인
7. GET /api/v1/sessions?chargerId={id} → 이력 1건 확인

**Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.evcharging.api.api.integration.ChargingFlowIntegrationTest" --info
```

기대 결과: 1 test PASSED (Docker-in-Docker로 Testcontainers가 PostgreSQL 컨테이너 자동 기동)

**Step 3: 전체 테스트 확인**

```bash
./gradlew test --info
```

기대 결과: 모든 테스트 PASSED

**Step 4: 커밋**

```bash
git add src/test/java/com/evcharging/api/api/integration/
git commit -m "test: add end-to-end charging flow integration test with Testcontainers"
```

---

## Task 13: 최종 점검 + Docker Compose 기동 테스트

**Step 1: 전체 테스트 실행**

```bash
./gradlew test
```

기대 결과: BUILD SUCCESSFUL, 모든 테스트 통과

**Step 2: Docker Compose로 전체 스택 기동 (프로덕션 배포용)**

```bash
docker compose up --build -d
```

기대 결과: app, postgres 컨테이너 모두 running

**Step 3: API 동작 확인**

```bash
curl http://localhost:8080/api/v1/stations | jq .
curl -s http://localhost:8080/swagger-ui.html | head -5
```

기대 결과: 충전소 목록 JSON 응답, Swagger UI HTML

**Step 4: Docker Compose 종료**

```bash
docker compose down
```

**Step 5: 커밋**

```bash
git add -A
git commit -m "chore: finalize MVP - all tests passing, Docker Compose verified"
```

---

## 태스크 요약

| Task | 내용 | 핵심 |
|------|------|------|
| 1 | Dev Container + 프로젝트 초기화 | .devcontainer/, build.gradle, application.yml |
| 2 | 프로덕션 Docker Compose + Dockerfile | docker-compose.yml, Dockerfile |
| 3 | 공통 모듈 | ApiResponse, ErrorCode, GlobalExceptionHandler |
| 4 | 충전기 상태 머신 + 테스트 | ChargerStatus Enum, 상태 전이 검증 |
| 5 | JPA 엔티티 | ChargingStation, Charger, ChargingSession |
| 6 | Repository 계층 | Haversine 위치 검색 쿼리 |
| 7 | Service 계층 + 단위 테스트 | 충전 세션 라이프사이클 로직 |
| 8 | DTO + Controller (충전소) | REST 엔드포인트 6개 |
| 9 | Controller (충전기 + 세션) | REST 엔드포인트 6개 |
| 10 | Swagger/OpenAPI 설정 | API 문서 자동 생성 |
| 11 | 공공 API 데이터 시딩 | 한국환경공단 API 연동 |
| 12 | 통합 테스트 | Testcontainers E2E 충전 플로우 |
| 13 | 최종 점검 | 전체 테스트 + Docker 기동 확인 |
