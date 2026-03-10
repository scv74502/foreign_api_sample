# foreign_api_sample

외부 API와 연동하는 서버에서 **장애 관리**, **작업 정합성 관리**를 주제로 한 샘플 프로젝트입니다.

## 사용 기술

| 기술 | 버전 |
|------|------|
| Kotlin | 1.9.25 |
| Spring Boot | 3.5.11 |
| JDK | 17 |
| MySQL | 8.0 |

## 주요 의존성

- Spring Data JPA
- Spring Web (MVC)
- Flyway Migration
- SpringDoc OpenAPI (Swagger UI) 2.8.16
- Testcontainers

## 환경 설정

### .env 파일

** .env 파일이 저장소에 포함된 이유**

별도 환경 설정 없이 즉시 실행할 수 있도록 `.env` 파일을 저장소에 포함했습니다.
편의성을 위한 설정일 뿐, 실제 프로덕션 환경에서는 .env 파일을 git에 포함하면 안 됩니다.
- `.gitignore`에 `.env` 추가 필수

#### .env 파일 관리 환경변수

| 환경변수 | 설명 | 기본값 |
|---------|------|-------|
| **MySQL 설정** |
| MYSQL_ROOT_PASSWORD | MySQL root 비밀번호 | test |
| MYSQL_USER | MySQL 사용자 | test |
| MYSQL_PASSWORD | MySQL 비밀번호 | test |
| MYSQL_DATABASE | 데이터베이스 이름 | foreign_api_sample |
| MYSQL_PORT | MySQL 포트 | 3306 |
| **Spring Datasource** |
| SPRING_DATASOURCE_URL | JDBC URL | jdbc:mysql://localhost:3306/foreign_api_sample |
| SPRING_DATASOURCE_USERNAME | 사용자명 | test |
| SPRING_DATASOURCE_PASSWORD | 비밀번호 | test |
| **JPA 설정** |
| SPRING_JPA_HIBERNATE_DDL_AUTO | DDL 자동 생성 모드 | validate |
| SPRING_JPA_SHOW_SQL | SQL 로그 출력 | true |
| **Test Datasource (Testcontainers)** |
| TEST_DATASOURCE_URL | 테스트용 JDBC URL | jdbc:tc:mysql:8.0:///foreign_api_sample |
| TEST_DATASOURCE_DRIVER | 테스트용 JDBC 드라이버 | org.testcontainers.jdbc.ContainerDatabaseDriver |
| **Test JPA 설정** |
| TEST_JPA_HIBERNATE_DDL_AUTO | 테스트 DDL 자동 생성 모드 | validate |
| TEST_JPA_SHOW_SQL | 테스트 SQL 로그 출력 | false |

### 데이터소스 구조

본 프로젝트는 **애플리케이션 실행**과 **테스트 실행**에서 서로 다른 데이터소스를 사용합니다:

| 환경 | 데이터소스 | 설명 |
|------|----------|------|
| **애플리케이션** | Docker Compose MySQL | 로컬 개발 시 `localhost:3306` |
| **테스트** | Testcontainers | 테스트 실행 시 자동으로 MySQL 컨테이너 생성 |

#### Testcontainers란?

- Docker를 활용한 통합 테스트 라이브러리
- 테스트 실행 시 자동으로 MySQL 컨테이너를 시작하고 종료
- `jdbc:tc:mysql:8.0:///` 프로토콜 사용
- 테스트 환경 격리 및 재현성 보장

## 실행 방법

### 1. MySQL 실행 (Docker Compose)

```bash
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 테스트 실행

```bash
./gradlew test
```

**참고:** 테스트 실행 시 Testcontainers가 자동으로 MySQL 컨테이너를 생성하므로, Docker Compose 실행 불필요합니다.

### 4. DB 재생성 (마이그레이션 스크립트 변경 시)

```bash
# 컨테이너 중지 및 볼륨 삭제
docker-compose down -v

# 재시작
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

## 아키텍처 및 기술 의사결정
상세한 분석 및 트레이드오프는 [analysis_document.md](analysis_document.md) 참조.

### 1. 비동기 처리

| 요구사항 | 고려 방안 | 선택 | 이유 |
|---------|----------|------|------|
| 외부 API 응답 지연<br>(수초~수십초) 대응 | @Async, WebFlux,<br>Coroutine, Virtual Thread | **Kotlin Coroutine** | 높은 동시성, 동기 코드 스타일 유지, JDK 17 환경에서 사용 가능.<br>Controller/Service는 동기로 유지하고 외부 호출만 논블로킹 처리 |

### 2. 상태 모델

| 요구사항 | 고려 방안 | 선택 | 이유 |
|---------|----------|------|------|
| 작업 생명주기 추적 및<br>서버 재시작 시 정합성 보장 | 외부 서비스 상태 그대로 사용<br>(3단계) vs 중간 상태 추가(4단계) | **4단계 FSM**<br>(PENDING, PROCESSING,<br>COMPLETED, FAILED) | DB 저장 후 외부 전달 전 구간을 명시적으로 표현.<br>서버 다운 시 작업 복구 가능. Enum + Optimistic Lock으로 상태 전이 정합성 보장 |

### 3. 멱등성 처리

| 요구사항 | 고려 방안 | 선택 | 이유 |
|---------|----------|------|------|
| 네트워크 재시도 등으로 인한<br>중복 요청 방지 | Debouncing, Throttling,<br>Idempotency Key | **Idempotency Key**<br>(X-Idempotency-Key 헤더) | HTTP API 중복 방지 표준 패턴.<br>DB UNIQUE 제약으로 원자적 중복 검사 가능.<br>디바운싱/스로틀링은 빈도 제어일 뿐 중복 실행 방지 불가 |

### 4. 장애 내성

| 요구사항 | 고려 방안 | 선택 | 이유 |
|---------|----------|------|------|
| 외부 서비스 불안정 대응<br>(500, 429, Timeout 등) | 수동 재시도, Spring Retry,<br>Resilience4j | **Resilience4j**<br>(Circuit Breaker, Retry,<br>Rate Limiter, Bulkhead) | 경량, 데코레이터 패턴으로 모듈 조합 가능.<br>Kotlin Coroutine 지원.<br>외부 장애 전파 차단 및 자동 복구 |

### 5. 처리 보장 모델

| 요구사항 | 고려 방안 | 선택 | 이유 |
|---------|----------|------|------|
| 분산 환경에서<br>작업 처리 보장 수준 정의 | At-Most-Once,<br>At-Least-Once,<br>Exactly-Once | **At-Least-Once** | 데이터 유실 방지 우선.<br>Exactly-Once는 구현 복잡도 과도.<br>Idempotency Key로 클라이언트 관점 중복 방지 |

### 6. 외부 시스템 연동

| 요구사항 | 고려 방안 | 선택 | 이유 |
|---------|----------|------|------|
| HTTP 클라이언트 선택 | RestTemplate, OpenFeign,<br>RestClient, WebClient | **WebClient** | Coroutine 통합(awaitBody), 논블로킹 I/O.<br>WebFlux 전면 도입 없이 HTTP 클라이언트만 활용 |
