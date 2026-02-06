# EV 충전소 관리 API 구현 계획

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**목표:** 한국환경공단 공공 API 기반의 EV 충전소 관리 REST API MVP 구현

**아키텍처:** 도메인 중심 패키지 구조(station/charger/session)로 Spring Boot 4.x REST API를 구현한다. 도메인 계층에서 비즈니스 로직(상태머신 등)을 캡슐화하고, API 계층에서 HTTP 관심사만 처리한다. 공공 API 데이터 시딩은 infra 패키지에서 분리하여 처리한다.

**기술 스택:** Java 25, Spring Boot 4.0.2, PostgreSQL 17, Spring Data JPA, Gradle 9.x, Testcontainers, SpringDoc OpenAPI 3.0.1, Docker Compose

**설계 문서:** `docs/plans/2026-02-06-ev-charging-api-design.md`

---

## Task 1: 프로젝트 초기화 (Gradle + Spring Boot)

**파일:**
- 생성: `build.gradle`
- 생성: `settings.gradle`
- 생성: `src/main/java/com/evcharging/api/EvChargingApiApplication.java`
- 생성: `src/main/resources/application.yml`
- 생성: `src/main/resources/application-local.yml`
- 생성: `.gitignore`
- 생성: `gradle/wrapper/gradle-wrapper.properties`

**Step 1: Gradle Wrapper 설치**

```bash
gradle wrapper --gradle-version 9.3.1
```

**Step 2: build.gradle 작성**

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

**Step 3: settings.gradle 작성**

```groovy
rootProject.name = 'ev-charging-api'
```

**Step 4: EvChargingApiApplication.java 작성**

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

**Step 5: application.yml 작성**

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

**Step 6: application-local.yml 작성**

```yaml
spring:
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

**Step 7: .gitignore 작성**

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

**Step 8: 빌드 확인**

```bash
./gradlew build -x test
```

기대 결과: BUILD SUCCESSFUL

**Step 9: 커밋**

```bash
git add build.gradle settings.gradle .gitignore gradle/ gradlew gradlew.bat \
  src/main/java/com/evcharging/api/EvChargingApiApplication.java \
  src/main/resources/application.yml src/main/resources/application-local.yml
git commit -m "chore: initialize Spring Boot 4.0.2 project with Gradle"
```

---

## Task 2: Docker Compose + PostgreSQL 설정

**파일:**
- 생성: `docker-compose.yml`
- 생성: `Dockerfile`

**Step 1: docker-compose.yml 작성**

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

**Step 3: PostgreSQL 컨테이너 기동 확인**

```bash
docker compose up postgres -d
docker compose ps
```

기대 결과: postgres 컨테이너 healthy 상태

**Step 4: PostgreSQL 종료**

```bash
docker compose down
```

**Step 5: 커밋**

```bash
git add docker-compose.yml Dockerfile
git commit -m "infra: add Docker Compose with PostgreSQL and multi-stage Dockerfile"
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

```java
package com.evcharging.api.domain.station;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.evcharging.api.domain.charger.Charger;

@Entity
@Table(name = "charging_station", indexes = {
        @Index(name = "idx_station_lat_lng", columnList = "latitude, longitude")
})
public class ChargingStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String stationCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private Double latitude;
    private Double longitude;
    private String operatorName;
    private String contactNumber;
    private String operatingHours;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Charger> chargers = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected ChargingStation() {}

    public ChargingStation(String stationCode, String name, String address,
                           Double latitude, Double longitude, String operatorName,
                           String contactNumber, String operatingHours) {
        this.stationCode = stationCode;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.operatorName = operatorName;
        this.contactNumber = contactNumber;
        this.operatingHours = operatingHours;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String name, String address, Double latitude, Double longitude,
                       String operatorName, String contactNumber, String operatingHours) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.operatorName = operatorName;
        this.contactNumber = contactNumber;
        this.operatingHours = operatingHours;
    }

    public void addCharger(Charger charger) {
        chargers.add(charger);
        charger.assignStation(this);
    }

    // Getters
    public Long getId() { return id; }
    public String getStationCode() { return stationCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getOperatorName() { return operatorName; }
    public String getContactNumber() { return contactNumber; }
    public String getOperatingHours() { return operatingHours; }
    public List<Charger> getChargers() { return chargers; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
```

**Step 2: Charger 엔티티 작성**

```java
package com.evcharging.api.domain.charger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.session.ChargingSession;

@Entity
@Table(name = "charger")
public class Charger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String chargerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargerStatus status;

    private BigDecimal powerKw;

    @Enumerated(EnumType.STRING)
    private ConnectorType connectorType;

    private LocalDateTime lastStatusChangedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id", nullable = false)
    private ChargingStation station;

    @OneToMany(mappedBy = "charger", cascade = CascadeType.ALL)
    private List<ChargingSession> sessions = new ArrayList<>();

    protected Charger() {}

    public Charger(String chargerCode, ChargerType type, BigDecimal powerKw,
                   ConnectorType connectorType) {
        this.chargerCode = chargerCode;
        this.type = type;
        this.status = ChargerStatus.AVAILABLE;
        this.powerKw = powerKw;
        this.connectorType = connectorType;
        this.lastStatusChangedAt = LocalDateTime.now();
    }

    public void changeStatus(ChargerStatus newStatus) {
        this.status.validateTransition(newStatus);
        this.status = newStatus;
        this.lastStatusChangedAt = LocalDateTime.now();
    }

    void assignStation(ChargingStation station) {
        this.station = station;
    }

    // Getters
    public Long getId() { return id; }
    public String getChargerCode() { return chargerCode; }
    public ChargerType getType() { return type; }
    public ChargerStatus getStatus() { return status; }
    public BigDecimal getPowerKw() { return powerKw; }
    public ConnectorType getConnectorType() { return connectorType; }
    public LocalDateTime getLastStatusChangedAt() { return lastStatusChangedAt; }
    public ChargingStation getStation() { return station; }
    public List<ChargingSession> getSessions() { return sessions; }
}
```

**Step 3: ChargingSession 엔티티 작성**

```java
package com.evcharging.api.domain.session;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.evcharging.api.domain.charger.Charger;

@Entity
@Table(name = "charging_session")
public class ChargingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "charger_id", nullable = false)
    private Charger charger;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;
    private BigDecimal energyDeliveredKwh;
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    protected ChargingSession() {}

    public static ChargingSession start(Charger charger) {
        ChargingSession session = new ChargingSession();
        session.charger = charger;
        session.startTime = LocalDateTime.now();
        session.status = SessionStatus.IN_PROGRESS;
        return session;
    }

    public void complete(BigDecimal energyDeliveredKwh, BigDecimal cost) {
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("진행 중인 세션만 완료할 수 있습니다");
        }
        this.endTime = LocalDateTime.now();
        this.energyDeliveredKwh = energyDeliveredKwh;
        this.cost = cost;
        this.status = SessionStatus.COMPLETED;
    }

    // Getters
    public Long getId() { return id; }
    public Charger getCharger() { return charger; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public BigDecimal getEnergyDeliveredKwh() { return energyDeliveredKwh; }
    public BigDecimal getCost() { return cost; }
    public SessionStatus getStatus() { return status; }
}
```

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

```java
package com.evcharging.api.domain.station;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StationRepository extends JpaRepository<ChargingStation, Long> {

    @Query(value = """
            SELECT s.*, (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude))
                    * cos(radians(s.longitude) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(s.latitude))
                )
            ) AS distance
            FROM charging_station s
            WHERE (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(s.latitude))
                    * cos(radians(s.longitude) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(s.latitude))
                )
            ) < :radius
            ORDER BY distance
            """, nativeQuery = true)
    List<ChargingStation> findNearby(@Param("lat") double lat,
                                     @Param("lng") double lng,
                                     @Param("radius") double radiusKm);

    boolean existsByStationCode(String stationCode);
}
```

**Step 2: ChargerRepository 작성**

```java
package com.evcharging.api.domain.charger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChargerRepository extends JpaRepository<Charger, Long> {

    List<Charger> findByStationId(Long stationId);
}
```

**Step 3: SessionRepository 작성**

```java
package com.evcharging.api.domain.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionRepository extends JpaRepository<ChargingSession, Long> {

    List<ChargingSession> findByChargerIdAndStartTimeBetween(
            Long chargerId, LocalDateTime start, LocalDateTime end);

    List<ChargingSession> findByChargerId(Long chargerId);
}
```

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

```java
package com.evcharging.api.domain.session;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.domain.charger.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    SessionRepository sessionRepository;
    @Mock
    ChargerRepository chargerRepository;
    @InjectMocks
    SessionService sessionService;

    @Test
    void 사용가능한_충전기로_충전을_시작한다() {
        Charger charger = new Charger("01", ChargerType.DC_FAST,
                new BigDecimal("50"), ConnectorType.CCS1);
        given(chargerRepository.findById(1L)).willReturn(Optional.of(charger));
        given(sessionRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        ChargingSession session = sessionService.startCharging(1L);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(charger.getStatus()).isEqualTo(ChargerStatus.CHARGING);
    }

    @Test
    void 사용불가능한_충전기로_충전시작_시_예외를_던진다() {
        Charger charger = new Charger("01", ChargerType.DC_FAST,
                new BigDecimal("50"), ConnectorType.CCS1);
        charger.changeStatus(ChargerStatus.OUT_OF_SERVICE);
        given(chargerRepository.findById(1L)).willReturn(Optional.of(charger));

        assertThatThrownBy(() -> sessionService.startCharging(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 진행중인_세션을_완료한다() {
        Charger charger = new Charger("01", ChargerType.DC_FAST,
                new BigDecimal("50"), ConnectorType.CCS1);
        charger.changeStatus(ChargerStatus.CHARGING);
        ChargingSession session = ChargingSession.start(charger);
        given(sessionRepository.findById(1L)).willReturn(Optional.of(session));

        ChargingSession completed = sessionService.completeCharging(
                1L, new BigDecimal("30.5"), new BigDecimal("15000"));

        assertThat(completed.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(completed.getEnergyDeliveredKwh()).isEqualByComparingTo(new BigDecimal("30.5"));
        assertThat(charger.getStatus()).isEqualTo(ChargerStatus.AVAILABLE);
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew test --tests "com.evcharging.api.domain.session.SessionServiceTest" --info
```

기대 결과: 컴파일 에러 (SessionService 없음)

**Step 3: SessionService 구현**

```java
package com.evcharging.api.domain.session;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerRepository;
import com.evcharging.api.domain.charger.ChargerStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ChargerRepository chargerRepository;

    public SessionService(SessionRepository sessionRepository,
                          ChargerRepository chargerRepository) {
        this.sessionRepository = sessionRepository;
        this.chargerRepository = chargerRepository;
    }

    @Transactional
    public ChargingSession startCharging(Long chargerId) {
        Charger charger = chargerRepository.findById(chargerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGER_NOT_FOUND));

        if (charger.getStatus() != ChargerStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.CHARGER_NOT_AVAILABLE);
        }

        charger.changeStatus(ChargerStatus.CHARGING);
        ChargingSession session = ChargingSession.start(charger);
        return sessionRepository.save(session);
    }

    @Transactional
    public ChargingSession completeCharging(Long sessionId,
                                            BigDecimal energyDeliveredKwh,
                                            BigDecimal cost) {
        ChargingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        session.complete(energyDeliveredKwh, cost);
        session.getCharger().changeStatus(ChargerStatus.AVAILABLE);
        return session;
    }

    public List<ChargingSession> findByCharger(Long chargerId,
                                               LocalDateTime start,
                                               LocalDateTime end) {
        if (start != null && end != null) {
            return sessionRepository.findByChargerIdAndStartTimeBetween(chargerId, start, end);
        }
        return sessionRepository.findByChargerId(chargerId);
    }
}
```

**Step 4: 테스트 통과 확인**

```bash
./gradlew test --tests "com.evcharging.api.domain.session.SessionServiceTest" --info
```

기대 결과: 3 tests PASSED

**Step 5: StationService 구현**

```java
package com.evcharging.api.domain.station;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    public Page<ChargingStation> findAll(Pageable pageable) {
        return stationRepository.findAll(pageable);
    }

    public ChargingStation findById(Long id) {
        return stationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.STATION_NOT_FOUND));
    }

    public List<ChargingStation> findNearby(double lat, double lng, double radiusKm) {
        return stationRepository.findNearby(lat, lng, radiusKm);
    }

    @Transactional
    public ChargingStation create(ChargingStation station) {
        return stationRepository.save(station);
    }

    @Transactional
    public ChargingStation update(Long id, String name, String address,
                                  Double latitude, Double longitude,
                                  String operatorName, String contactNumber,
                                  String operatingHours) {
        ChargingStation station = findById(id);
        station.update(name, address, latitude, longitude,
                operatorName, contactNumber, operatingHours);
        return station;
    }

    @Transactional
    public void delete(Long id) {
        ChargingStation station = findById(id);
        stationRepository.delete(station);
    }
}
```

**Step 6: ChargerService 구현**

```java
package com.evcharging.api.domain.charger;

import com.evcharging.api.common.BusinessException;
import com.evcharging.api.common.ErrorCode;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ChargerService {

    private final ChargerRepository chargerRepository;
    private final StationService stationService;

    public ChargerService(ChargerRepository chargerRepository,
                          StationService stationService) {
        this.chargerRepository = chargerRepository;
        this.stationService = stationService;
    }

    public List<Charger> findByStation(Long stationId) {
        return chargerRepository.findByStationId(stationId);
    }

    @Transactional
    public Charger create(Long stationId, Charger charger) {
        ChargingStation station = stationService.findById(stationId);
        station.addCharger(charger);
        return chargerRepository.save(charger);
    }

    @Transactional
    public Charger changeStatus(Long chargerId, ChargerStatus newStatus) {
        Charger charger = chargerRepository.findById(chargerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHARGER_NOT_FOUND));
        charger.changeStatus(newStatus);
        return charger;
    }
}
```

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
- 테스트: `src/test/java/com/evcharging/api/api/station/StationControllerTest.java`

**Step 1: 실패하는 테스트 작성**

```java
package com.evcharging.api.api.station;

import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StationController.class)
class StationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StationService stationService;

    @Test
    void 충전소_상세를_조회한다() throws Exception {
        ChargingStation station = new ChargingStation(
                "ST001", "강남역 충전소", "서울특별시 강남구 강남대로 396",
                37.4979, 127.0276, "GS차지비", "1544-1234", "24시간");
        given(stationService.findById(1L)).willReturn(station);

        mockMvc.perform(get("/api/v1/stations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("강남역 충전소"))
                .andExpect(jsonPath("$.data.address").value("서울특별시 강남구 강남대로 396"));
    }
}
```

**Step 2: 테스트 실행하여 실패 확인**

```bash
./gradlew test --tests "com.evcharging.api.api.station.StationControllerTest" --info
```

기대 결과: 컴파일 에러 (StationController, StationResponse 없음)

**Step 3: DTO 구현**

`StationRequest.java`:

```java
package com.evcharging.api.api.station;

import jakarta.validation.constraints.NotBlank;

public record StationRequest(
        String stationCode,
        @NotBlank String name,
        @NotBlank String address,
        Double latitude,
        Double longitude,
        String operatorName,
        String contactNumber,
        String operatingHours
) {}
```

`StationResponse.java`:

```java
package com.evcharging.api.api.station;

import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.api.charger.ChargerResponse;

import java.util.List;

public record StationResponse(
        Long id,
        String stationCode,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String operatorName,
        String contactNumber,
        String operatingHours,
        List<ChargerResponse> chargers
) {
    public static StationResponse from(ChargingStation station) {
        return new StationResponse(
                station.getId(),
                station.getStationCode(),
                station.getName(),
                station.getAddress(),
                station.getLatitude(),
                station.getLongitude(),
                station.getOperatorName(),
                station.getContactNumber(),
                station.getOperatingHours(),
                station.getChargers().stream()
                        .map(ChargerResponse::from)
                        .toList()
        );
    }
}
```

**Step 4: ChargerResponse도 함께 생성 (StationResponse에서 참조)**

```java
package com.evcharging.api.api.charger;

import com.evcharging.api.domain.charger.Charger;

import java.math.BigDecimal;

public record ChargerResponse(
        Long id,
        String chargerCode,
        String type,
        String status,
        BigDecimal powerKw,
        String connectorType
) {
    public static ChargerResponse from(Charger charger) {
        return new ChargerResponse(
                charger.getId(),
                charger.getChargerCode(),
                charger.getType().name(),
                charger.getStatus().name(),
                charger.getPowerKw(),
                charger.getConnectorType() != null ? charger.getConnectorType().name() : null
        );
    }
}
```

**Step 5: StationController 구현**

```java
package com.evcharging.api.api.station;

import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public ApiResponse<Page<StationResponse>> findAll(Pageable pageable) {
        Page<StationResponse> page = stationService.findAll(pageable)
                .map(StationResponse::from);
        return ApiResponse.success(page);
    }

    @GetMapping("/nearby")
    public ApiResponse<List<StationResponse>> findNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") double radius) {
        List<StationResponse> stations = stationService.findNearby(lat, lng, radius)
                .stream().map(StationResponse::from).toList();
        return ApiResponse.success(stations);
    }

    @GetMapping("/{id}")
    public ApiResponse<StationResponse> findById(@PathVariable Long id) {
        ChargingStation station = stationService.findById(id);
        return ApiResponse.success(StationResponse.from(station));
    }

    @PostMapping
    public ApiResponse<StationResponse> create(@Valid @RequestBody StationRequest request) {
        ChargingStation station = new ChargingStation(
                request.stationCode(), request.name(), request.address(),
                request.latitude(), request.longitude(), request.operatorName(),
                request.contactNumber(), request.operatingHours());
        ChargingStation saved = stationService.create(station);
        return ApiResponse.success(StationResponse.from(saved));
    }

    @PutMapping("/{id}")
    public ApiResponse<StationResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody StationRequest request) {
        ChargingStation updated = stationService.update(id, request.name(), request.address(),
                request.latitude(), request.longitude(), request.operatorName(),
                request.contactNumber(), request.operatingHours());
        return ApiResponse.success(StationResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        stationService.delete(id);
        return ApiResponse.success(null);
    }
}
```

**Step 6: 테스트 통과 확인**

```bash
./gradlew test --tests "com.evcharging.api.api.station.StationControllerTest" --info
```

기대 결과: 1 test PASSED

**Step 7: 커밋**

```bash
git add src/main/java/com/evcharging/api/api/
git add src/test/java/com/evcharging/api/api/
git commit -m "feat: add station REST controller with DTO layer"
```

---

## Task 9: Controller (충전기 + 세션)

**파일:**
- 생성: `src/main/java/com/evcharging/api/api/charger/ChargerController.java`
- 생성: `src/main/java/com/evcharging/api/api/charger/ChargerStatusRequest.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionController.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionStartRequest.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionCompleteRequest.java`
- 생성: `src/main/java/com/evcharging/api/api/session/SessionResponse.java`

**Step 1: DTO 작성**

`ChargerStatusRequest.java`:

```java
package com.evcharging.api.api.charger;

import com.evcharging.api.domain.charger.ChargerStatus;
import jakarta.validation.constraints.NotNull;

public record ChargerStatusRequest(@NotNull ChargerStatus status) {}
```

`SessionStartRequest.java`: (빈 body — 향후 확장용)

```java
package com.evcharging.api.api.session;

public record SessionStartRequest() {}
```

`SessionCompleteRequest.java`:

```java
package com.evcharging.api.api.session;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SessionCompleteRequest(
        @NotNull @Positive BigDecimal energyDeliveredKwh,
        @NotNull @Positive BigDecimal cost
) {}
```

`SessionResponse.java`:

```java
package com.evcharging.api.api.session;

import com.evcharging.api.domain.session.ChargingSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SessionResponse(
        Long id,
        Long chargerId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal energyDeliveredKwh,
        BigDecimal cost,
        String status
) {
    public static SessionResponse from(ChargingSession session) {
        return new SessionResponse(
                session.getId(),
                session.getCharger().getId(),
                session.getStartTime(),
                session.getEndTime(),
                session.getEnergyDeliveredKwh(),
                session.getCost(),
                session.getStatus().name()
        );
    }
}
```

**Step 2: ChargerController 구현**

```java
package com.evcharging.api.api.charger;

import com.evcharging.api.api.station.StationRequest;
import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.charger.Charger;
import com.evcharging.api.domain.charger.ChargerService;
import com.evcharging.api.domain.charger.ChargerType;
import com.evcharging.api.domain.charger.ConnectorType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ChargerController {

    private final ChargerService chargerService;

    public ChargerController(ChargerService chargerService) {
        this.chargerService = chargerService;
    }

    @GetMapping("/api/v1/stations/{stationId}/chargers")
    public ApiResponse<List<ChargerResponse>> findByStation(@PathVariable Long stationId) {
        List<ChargerResponse> chargers = chargerService.findByStation(stationId)
                .stream().map(ChargerResponse::from).toList();
        return ApiResponse.success(chargers);
    }

    @PostMapping("/api/v1/stations/{stationId}/chargers")
    public ApiResponse<ChargerResponse> create(@PathVariable Long stationId,
                                               @Valid @RequestBody CreateChargerRequest request) {
        Charger charger = new Charger(request.chargerCode(), request.type(),
                request.powerKw(), request.connectorType());
        Charger saved = chargerService.create(stationId, charger);
        return ApiResponse.success(ChargerResponse.from(saved));
    }

    @PatchMapping("/api/v1/chargers/{id}/status")
    public ApiResponse<ChargerResponse> changeStatus(@PathVariable Long id,
                                                     @Valid @RequestBody ChargerStatusRequest request) {
        Charger charger = chargerService.changeStatus(id, request.status());
        return ApiResponse.success(ChargerResponse.from(charger));
    }

    public record CreateChargerRequest(
            String chargerCode,
            ChargerType type,
            BigDecimal powerKw,
            ConnectorType connectorType
    ) {}
}
```

**Step 3: SessionController 구현**

```java
package com.evcharging.api.api.session;

import com.evcharging.api.common.ApiResponse;
import com.evcharging.api.domain.session.ChargingSession;
import com.evcharging.api.domain.session.SessionService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/api/v1/chargers/{chargerId}/sessions")
    public ApiResponse<SessionResponse> startCharging(@PathVariable Long chargerId) {
        ChargingSession session = sessionService.startCharging(chargerId);
        return ApiResponse.success(SessionResponse.from(session));
    }

    @PatchMapping("/api/v1/sessions/{id}/complete")
    public ApiResponse<SessionResponse> completeCharging(
            @PathVariable Long id,
            @Valid @RequestBody SessionCompleteRequest request) {
        ChargingSession session = sessionService.completeCharging(
                id, request.energyDeliveredKwh(), request.cost());
        return ApiResponse.success(SessionResponse.from(session));
    }

    @GetMapping("/api/v1/sessions")
    public ApiResponse<List<SessionResponse>> findSessions(
            @RequestParam Long chargerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<SessionResponse> sessions = sessionService.findByCharger(chargerId, startDate, endDate)
                .stream().map(SessionResponse::from).toList();
        return ApiResponse.success(sessions);
    }
}
```

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

```java
package com.evcharging.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI evChargingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("EV 충전소 관리 API")
                        .description("전기차 충전소 관리 REST API - 충전소 검색, 충전기 상태 관리, 충전 이력 조회")
                        .version("v1.0.0")
                        .contact(new Contact().name("Kim Kwanghyeok")));
    }
}
```

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

**Step 1: EvChargerApiResponse 작성 (API 응답 매핑)**

```java
package com.evcharging.api.infra.openapi;

import java.util.List;

public record EvChargerApiResponse(
        Response response
) {
    public record Response(Header header, Body body) {}
    public record Header(String resultCode, String resultMsg) {}
    public record Body(Items items, int totalCount, int pageNo, int numOfRows) {}
    public record Items(List<Item> item) {}
    public record Item(
            String statNm,    // 충전소명
            String statId,    // 충전소 ID
            String chgerId,   // 충전기 ID
            String chgerType, // 충전기 타입
            String addr,      // 주소
            String lat,       // 위도
            String lng,       // 경도
            String busiNm,    // 운영기관명
            String busiCall,  // 연락처
            String useTime,   // 이용가능시간
            String stat,      // 상태 (1:통신이상, 2:사용가능, 3:충전중, 4:운영중지, 5:점검중, 9:상태미확인)
            String output     // 충전용량
    ) {}
}
```

**Step 2: EvChargerApiClient 작성**

```java
package com.evcharging.api.infra.openapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component
public class EvChargerApiClient {

    private static final Logger log = LoggerFactory.getLogger(EvChargerApiClient.class);
    private static final String BASE_URL = "http://apis.data.go.kr/B552584/EvCharger";

    private final RestClient restClient;
    private final String serviceKey;

    public EvChargerApiClient(@Value("${openapi.service-key:}") String serviceKey) {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .build();
        this.serviceKey = serviceKey;
    }

    public List<EvChargerApiResponse.Item> fetchSeoulChargers(int pageNo, int numOfRows) {
        try {
            EvChargerApiResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/getChargerInfo")
                            .queryParam("serviceKey", serviceKey)
                            .queryParam("pageNo", pageNo)
                            .queryParam("numOfRows", numOfRows)
                            .queryParam("zcode", "11")
                            .queryParam("dataType", "JSON")
                            .build())
                    .retrieve()
                    .body(EvChargerApiResponse.class);

            if (response != null && response.response() != null
                    && response.response().body() != null
                    && response.response().body().items() != null) {
                return response.response().body().items().item();
            }
        } catch (Exception e) {
            log.warn("공공 API 호출 실패: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
```

**Step 3: DataSeeder 작성**

```java
package com.evcharging.api.infra.openapi;

import com.evcharging.api.domain.charger.*;
import com.evcharging.api.domain.station.ChargingStation;
import com.evcharging.api.domain.station.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final StationRepository stationRepository;
    private final EvChargerApiClient apiClient;

    public DataSeeder(StationRepository stationRepository, EvChargerApiClient apiClient) {
        this.stationRepository = stationRepository;
        this.apiClient = apiClient;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stationRepository.count() > 0) {
            log.info("DB에 이미 데이터가 있습니다. 시딩을 건너뜁니다.");
            return;
        }

        List<EvChargerApiResponse.Item> items = apiClient.fetchSeoulChargers(1, 100);
        if (items.isEmpty()) {
            log.warn("공공 API 데이터가 비어있습니다. 폴백 데이터는 data.sql로 처리됩니다.");
            return;
        }

        Map<String, ChargingStation> stationMap = new LinkedHashMap<>();
        for (EvChargerApiResponse.Item item : items) {
            ChargingStation station = stationMap.computeIfAbsent(item.statId(), id ->
                    new ChargingStation(
                            item.statId(), item.statNm(), item.addr(),
                            parseDouble(item.lat()), parseDouble(item.lng()),
                            item.busiNm(), item.busiCall(), item.useTime()));

            Charger charger = new Charger(
                    item.chgerId(),
                    mapChargerType(item.chgerType()),
                    parsePowerKw(item.output()),
                    mapConnectorType(item.chgerType()));
            station.addCharger(charger);
        }

        stationRepository.saveAll(stationMap.values());
        log.info("공공 API에서 {}개 충전소, {}개 충전기를 시딩했습니다.",
                stationMap.size(),
                stationMap.values().stream().mapToInt(s -> s.getChargers().size()).sum());
    }

    private ChargerType mapChargerType(String code) {
        return switch (code) {
            case "01" -> ChargerType.DC_FAST;     // DC차데모
            case "02" -> ChargerType.AC_SLOW;     // AC완속
            case "03" -> ChargerType.DC_FAST;     // DC차데모+AC3상
            case "04" -> ChargerType.DC_COMBO;    // DC콤보
            case "05" -> ChargerType.DC_FAST;     // DC차데모+DC콤보
            case "06" -> ChargerType.DC_COMBO;    // DC차데모+AC3상+DC콤보
            case "07" -> ChargerType.AC_SLOW;     // AC3상
            default -> ChargerType.DC_FAST;
        };
    }

    private ConnectorType mapConnectorType(String code) {
        return switch (code) {
            case "01", "03", "05" -> ConnectorType.CHADEMO;
            case "02" -> ConnectorType.AC_TYPE_1;
            case "04", "06" -> ConnectorType.CCS1;
            case "07" -> ConnectorType.AC_TYPE_3;
            default -> ConnectorType.CCS1;
        };
    }

    private Double parseDouble(String value) {
        try { return Double.parseDouble(value); }
        catch (Exception e) { return null; }
    }

    private BigDecimal parsePowerKw(String value) {
        try { return new BigDecimal(value); }
        catch (Exception e) { return null; }
    }
}
```

**Step 4: data.sql 폴백 데이터 작성**

```sql
-- 폴백 샘플 데이터 (공공 API 호출 실패 시 사용)
-- spring.sql.init.mode=always 일 때만 실행됨

INSERT INTO charging_station (station_code, name, address, latitude, longitude, operator_name, contact_number, operating_hours, created_at, updated_at)
VALUES
    ('ME000001', '강남역 충전소', '서울특별시 강남구 강남대로 396', 37.4979, 127.0276, 'GS차지비', '1544-1234', '24시간', NOW(), NOW()),
    ('ME000002', '여의도 IFC 충전소', '서울특별시 영등포구 국제금융로 10', 37.5252, 126.9256, 'GS차지비', '1544-1234', '24시간', NOW(), NOW()),
    ('ME000003', '코엑스 충전소', '서울특별시 강남구 영동대로 513', 37.5118, 127.0590, '한국전력', '1588-3200', '06:00~23:00', NOW(), NOW()),
    ('ME000004', '서울역 충전소', '서울특별시 중구 한강대로 405', 37.5547, 126.9707, 'GS차지비', '1544-1234', '24시간', NOW(), NOW()),
    ('ME000005', '잠실 롯데타워 충전소', '서울특별시 송파구 올림픽로 300', 37.5126, 127.1026, '환경부', '1661-9408', '24시간', NOW(), NOW());

INSERT INTO charger (charger_code, type, status, power_kw, connector_type, last_status_changed_at, station_id)
VALUES
    ('01', 'DC_FAST', 'AVAILABLE', 50, 'CCS1', NOW(), 1),
    ('02', 'AC_SLOW', 'AVAILABLE', 7, 'AC_TYPE_1', NOW(), 1),
    ('01', 'DC_COMBO', 'CHARGING', 100, 'CCS1', NOW(), 2),
    ('02', 'DC_FAST', 'AVAILABLE', 50, 'CHADEMO', NOW(), 2),
    ('01', 'DC_FAST', 'AVAILABLE', 50, 'CCS1', NOW(), 3),
    ('02', 'DC_FAST', 'OUT_OF_SERVICE', 50, 'CCS1', NOW(), 3),
    ('01', 'AC_SLOW', 'AVAILABLE', 7, 'AC_TYPE_1', NOW(), 4),
    ('02', 'DC_FAST', 'AVAILABLE', 50, 'CCS1', NOW(), 4),
    ('01', 'DC_COMBO', 'AVAILABLE', 100, 'CCS1', NOW(), 5),
    ('02', 'AC_SLOW', 'AVAILABLE', 7, 'AC_TYPE_1', NOW(), 5);
```

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

```java
package com.evcharging.api.api.integration;

import com.evcharging.api.api.charger.ChargerController;
import com.evcharging.api.api.session.SessionCompleteRequest;
import com.evcharging.api.api.station.StationRequest;
import com.evcharging.api.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ChargingFlowIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("openapi.service-key", () -> "");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 충전소_등록부터_충전완료까지_전체_플로우를_테스트한다() throws Exception {
        // 1. 충전소 등록
        StationRequest stationReq = new StationRequest(
                "TEST001", "테스트 충전소", "서울특별시 강남구 테헤란로 123",
                37.5065, 127.0536, "테스트운영사", "02-1234-5678", "24시간");

        MvcResult stationResult = mockMvc.perform(post("/api/v1/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stationReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("테스트 충전소"))
                .andReturn();

        Long stationId = objectMapper.readTree(
                stationResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 2. 충전기 등록
        ChargerController.CreateChargerRequest chargerReq = new ChargerController.CreateChargerRequest(
                "01", com.evcharging.api.domain.charger.ChargerType.DC_FAST,
                new BigDecimal("50"), com.evcharging.api.domain.charger.ConnectorType.CCS1);

        MvcResult chargerResult = mockMvc.perform(post("/api/v1/stations/" + stationId + "/chargers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chargerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"))
                .andReturn();

        Long chargerId = objectMapper.readTree(
                chargerResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 3. 충전 시작
        MvcResult sessionResult = mockMvc.perform(post("/api/v1/chargers/" + chargerId + "/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
                .andReturn();

        Long sessionId = objectMapper.readTree(
                sessionResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 4. 충전기 상태 확인 (CHARGING)
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/chargers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CHARGING"));

        // 5. 충전 완료
        SessionCompleteRequest completeReq = new SessionCompleteRequest(
                new BigDecimal("30.5"), new BigDecimal("15000"));

        mockMvc.perform(patch("/api/v1/sessions/" + sessionId + "/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.energyDeliveredKwh").value(30.5));

        // 6. 충전기 상태 확인 (AVAILABLE 복귀)
        mockMvc.perform(get("/api/v1/stations/" + stationId + "/chargers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("AVAILABLE"));

        // 7. 충전 이력 확인
        mockMvc.perform(get("/api/v1/sessions?chargerId=" + chargerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
```

**Step 2: 테스트 실행**

```bash
./gradlew test --tests "com.evcharging.api.api.integration.ChargingFlowIntegrationTest" --info
```

기대 결과: 1 test PASSED (Docker 필요 - Testcontainers가 PostgreSQL 컨테이너를 자동 기동)

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

**Step 2: Docker Compose로 전체 스택 기동**

```bash
docker compose up --build -d
```

기대 결과: app, postgres 컨테이너 모두 running

**Step 3: API 동작 확인**

```bash
curl http://localhost:8080/api/v1/stations | jq .
curl http://localhost:8080/swagger-ui.html
```

기대 결과: 충전소 목록 JSON 응답, Swagger UI 페이지

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

| Task | 내용 | 예상 시간 |
|------|------|----------|
| 1 | 프로젝트 초기화 (Gradle + Spring Boot) | 15분 |
| 2 | Docker Compose + PostgreSQL | 10분 |
| 3 | 공통 모듈 (ApiResponse, ErrorCode) | 15분 |
| 4 | 충전기 상태 머신 + 테스트 | 15분 |
| 5 | JPA 엔티티 | 20분 |
| 6 | Repository 계층 | 10분 |
| 7 | Service 계층 + 단위 테스트 | 25분 |
| 8 | DTO + Controller (충전소) | 20분 |
| 9 | Controller (충전기 + 세션) | 15분 |
| 10 | Swagger/OpenAPI 설정 | 5분 |
| 11 | 공공 API 데이터 시딩 | 20분 |
| 12 | 통합 테스트 (Testcontainers) | 15분 |
| 13 | 최종 점검 + Docker 기동 테스트 | 10분 |
