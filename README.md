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
- Spring WebFlux (WebClient only)
- Flyway Migration
- SpringDoc OpenAPI (Swagger UI) 2.8.16
- Resilience4j (Circuit Breaker, Rate Limiter, Bulkhead)
- Kotlin Coroutines
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
| **애플리케이션** |
| SERVER_PORT | 앱 서버 호스트 포트 | 8080 |
| **MySQL 설정** |
| MYSQL_ROOT_PASSWORD | MySQL root 비밀번호 | test |
| MYSQL_USER | MySQL 사용자 | test |
| MYSQL_PASSWORD | MySQL 비밀번호 | test |
| MYSQL_DATABASE | 데이터베이스 이름 | foreign_api_sample |
| MYSQL_PORT | MySQL 호스트 포트 | 3306 |
| **Spring Datasource** |
| SPRING_DATASOURCE_URL | JDBC URL | jdbc:mysql://localhost:3306/foreign_api_sample?useSSL=false&allowPublicKeyRetrieval=true |
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
| **Mock Worker** |
| MOCK_WORKER_BASE_URL | 외부 서비스 기본 URL | https://dev.realteeth.ai/mock |
| MOCK_WORKER_CANDIDATE_NAME | 후보자 이름 | scv74502 |
| MOCK_WORKER_EMAIL | 후보자 이메일 | scv74502@gmail.com |

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

## 포트 정보

| 서비스 | 환경변수 | 기본값 | 설명 |
|--------|---------|--------|------|
| 애플리케이션 | `SERVER_PORT` | 8080 | Spring Boot 서버 (호스트 포트) |
| MySQL | `MYSQL_PORT` | 3306 | 데이터베이스 (호스트 포트) |
| Swagger UI | - | 8080 | `http://localhost:{SERVER_PORT}/swagger-ui/index.html` |

포트를 변경하려면 `.env` 파일에서 해당 환경변수를 수정합니다:
```bash
# 예: 앱을 9090, MySQL을 13306 포트로 변경
SERVER_PORT=9090
MYSQL_PORT=13306
```

## 실행 방법

### 방법 1: Docker Compose 전체 스택 (권장)

앱과 MySQL을 한 번에 실행합니다.

```bash
docker-compose up -d
```

정상 기동 확인:
```bash
# 컨테이너 상태 확인
docker-compose ps

# API 접근 확인
curl http://localhost:8080/swagger-ui/index.html
```

종료:
```bash
docker-compose down
```

### 방법 2: 개별 실행 (로컬 개발)

#### 1. MySQL 실행

```bash
docker-compose up -d mysql
```

#### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 테스트 실행

```bash
./gradlew test
```

**참고:** 테스트 실행 시 Testcontainers가 자동으로 MySQL 컨테이너를 생성하므로, Docker Compose 실행 불필요합니다.

### DB 재생성 (마이그레이션 스크립트 변경 시)

```bash
# 컨테이너 중지 및 볼륨 삭제
docker-compose down -v

# 재시작
docker-compose up -d
```

## 시스템 개요

클라이언트로부터 이미지 처리 요청을 수신하고, 외부 이미지 처리 딥러닝 서버에 작업을 위임하여 작업 결과를 추적·반환하는 중간 서버입니다.
외부 서비스는 GPU 기반 AI 추론(딥러닝 모델)을 수행하며, 응답 시간이 수 초에서 수십 초까지 크게 변동됩니다.
순간 트래픽이 몰릴 경우 안정적으로 응답을 받기 어려우며, 내부 동작 특성은 사전에 예측하기 어렵고 자주 변경됩니다.
또한 이 중간 서버가 갑자기 종료되더라도, 작업 내용의 정합성은 유지되어야 합니다.

## API 목록

### 엔드포인트

| # | 엔드포인트 | 메서드 | 설명 |
|---|---|---|---|
| 1 | `/api/tasks` | POST | 이미지 처리 요청 수신, 작업 ID 발급 |
| 2 | `/api/tasks/{taskId}` | GET | 작업 상태 및 결과 조회 |
| 3 | `/api/tasks` | GET | 작업 목록 조회 (페이징, 상태 필터는 URL QueryString에) |

### 기능별 상세

**API 1: 이미지 처리 요청**
- 클라이언트가 이미지 URL과 멱등성 키를 전달합니다.
- 서버는 작업을 DB에 저장하고 즉시 작업 ID를 반환합니다 (202 Accepted).
- 실제 처리는 백그라운드에서 비동기로 수행됩니다.

**API 2: 작업 상태 조회**
- 작업의 현재 상태(대기, 처리 중, 완료, 실패)를 반환합니다.
- 완료 시 처리 결과를, 실패 시 에러 코드와 사유를 포함합니다.

**API 3: 작업 목록 조회**
- 상태별 필터링과 페이지네이션을 지원합니다.
- 쿼리 파라미터:
  - `page`: 페이지 번호 (0부터 시작, 기본값: 0)
  - `size`: 페이지 크기 (기본값: 20, 최대: 50)
  - `status`: 상태 필터 (PENDING, PROCESSING, COMPLETED, FAILED, 미지정 시 전체)
- 정렬: 생성일시 내림차순 (`createdAt DESC`)
- 응답 형식: Spring Data의 `Page<Task>` (totalElements, totalPages, content 포함)

## 기술적 문제 분석 및 해결

### 3.1 비동기 처리 — 외부 서비스 응답 지연

**문제 인식:**
외부 서비스의 응답 시간이 수 초에서 수십 초까지 변동됩니다. 동기 방식으로 처리하면 클라이언트가 수십 초간 응답을 대기해야 하며, 서버 스레드가 장시간 점유되어 동시 처리 능력이 급격히 저하됩니다.

**사고 과정:**
톰캣의 스레드 풀은 최소-최대 수를 지정할 수 있고, 임시적으로는 적용할 수 있는 방법입니다.
하지만 미리 환경을 알고 구현하는 경우에는 비동기 처리를 활용하여 스레드 대비 외부 IO 처리를 효율적으로 하는 방법을 적용해야 합니다.
이러한 경우 @Async 어노테이션, WebFlux를 통한 io 효율 극대화, 코루틴, virtual thread 등의 방법이 자주 사용됩니다.

| 방식 | 장점 | 단점 |
|---|---|---|
| @Async + 플랫폼 스레드 풀 | 단순, Spring 기본 지원 | 스레드 풀 크기에 동시성 제한, 스레드당 ~1MB 메모리 |
| WebFlux (리액티브) 전면 도입 | 높은 동시성, 논블로킹 | 코드 전체가 리액티브 체인으로 변경, 리액티브 패턴에 익숙하지 않아 높은 학습 곡선 |
| **Kotlin Coroutine** | 높은 동시성, 동기 코드 스타일 유지 가능, 스레드 대비 극히 경량이며 context switch 비용 적음, 코틀린 자체 기능이라 JDK 17에 적용 용이 | suspend 전파 범위 관리 필요 |
| **virtual thread** | 높은 동시성, 동기 코드 스타일 유지 가능, 플랫폼 스레드 대비 극히 경량이며 context switch 비용 적음, 기존 자바/코틀린 코드에 적용이 쉬움 | JDK 21 이상 환경 필요 |

**선정한 해결법:**
Kotlin Coroutine을 외부 딥러닝 서버 호출 부분에 적용합니다.

- Controller와 Service 레이어는 완전히 동기(Spring MVC)로 유지합니다.
- 비동기 처리 전담 컴포넌트가 코루틴 경계 역할을 하며, 내부에서 `CoroutineScope.launch`로 비동기 작업을 시작합니다.
- 외부 서비스 호출은 `suspend fun` + WebClient의 `awaitBody()`로 논블로킹 처리합니다.
- Polling 대기는 `delay()`를 사용하여 스레드를 양보합니다 (`Thread.sleep()`과 달리 스레드를 점유하지 않음).

**이 방식을 선택한 이유:**
리액티브 전면 도입 없이도 외부 호출 부분만 논블로킹으로 전환하여, 코드 복잡도는 최소화하면서 동시성은 극대화할 수 있습니다. `suspend` 전파 범위가 Orchestrator 아래로 한정되어 기존 Spring MVC 패턴에 영향을 주지 않습니다.
무엇보다도 virtual thread는 jdk 21 이상으로 마이그레이션을 필수적으로 요구하지만, 코루틴은 코틀린을 사용하기만 한다면 레거시 코드에서도 쉽게 활용이 가능합니다.

```
코루틴 경계 구조:

Controller (동기) → Service (동기) → Orchestrator.submitAsync() ← 경계
                                          ↓ (코루틴 내부)
                                     외부 서비스 Client (suspend)
                                          ↓
                                     외부 서비스 API (suspend + awaitBody)
```

---

### 3.2 상태 모델 — 작업 생명주기 추적

**문제 인식:**
외부 서비스의 상태 모델은 PROCESSING, COMPLETED, FAILED 세 가지입니다. 그러나 "요청은 수신했지만 아직 외부 서비스에 전달하지 않은" 단계가 존재합니다.
이 간극을 표현하지 않으면 서버 장애 시 해당 작업의 복구가 불가능합니다.

**사고 과정:**
우선 작업 요청 내역을 영속화하는 데에는 메세지 큐, 데이터베이스, Redis, 스프링의 작업 큐 등을 활용할 수 있습니다.
각 기술에 대한 학습/인프라 관리 비용과 디스크 기반의 영속성 등을 고려할 때, 요청받은 작업을 데이터베이스에 저장하는 방법을 선택했습니다.

또한 DB에 저장하는 시점과 외부 서비스에 실제로 전달하는 시점 사이에 시간 간극이 존재합니다.
만약 이 구간에서 서버가 다운되면, 해당 작업이 외부 서비스에 전달되었는지 여부를 알 수 없습니다.
따라서 이를 명시적으로 구분하기 위해 작업을 수신하였으나 서비스에 전달하진 않은 중간 단계의 PENDING 상태가 필요합니다.

**해결법:**
5단계 상태 모델을 정의합니다:

| 상태 | 의미 |
|---|---|
| PENDING | 요청 수신 완료, 외부 서비스에 미전달 |
| PROCESSING | 외부 서비스에 전달됨, 작업 ID 보유 |
| COMPLETED | 처리 완료, 결과 보유 |
| FAILED | 처리 실패 (재시도 횟수에 따라 종료 또는 재시도) |
| CANCELLED | 작업 취소 (향후 요구사항 대비) |

```
상태 전이 다이어그램:

PENDING ──→ PROCESSING ──→ COMPLETED
   │             │
   │             ├──→ PENDING (재시도, retryCount < maxRetry, 직접 전이)
   │             │
   │             ├──→ FAILED (retryCount ≥ maxRetry 또는 영구 실패)
   │             │        └──→ [종료]
   │             │
   │             └──→ CANCELLED
   │
   └──→ CANCELLED
```

**상태 전이 정합성 보장:**

- 코드 레벨: Enum 내부에 `canTransitionTo()` 메서드를 정의하여, 허용되지 않는 전이(예: COMPLETED → PROCESSING) 시도 시 예외를 발생시킵니다.
- DB 레벨: JPA `@Version` 기반 Optimistic Locking으로, 동시에 두 스레드가 같은 작업의 상태를 변경하려 할 때 하나만 성공하도록 보장합니다.

**이 방식을 선택한 이유:**
FSM(유한 상태 머신)을 Enum으로 직접 구현하면 상태가 4개뿐인 이 시스템에서 충분하며, Spring Statemachine 같은 프레임워크 도입 대비 복잡도가 낮습니다. Optimistic Lock은 Pessimistic Lock 대비 DB 레벨 잠금이 없어 동시성 처리에 유리합니다.

---

### 3.3 중복 요청 처리 — 멱등성(Idempotency)

**문제 인식:**
네트워크 재시도, 클라이언트 버그, 프록시 재전송 등으로 동일한 요청이 여러 번 도달할 수 있습니다. 중복 요청마다 새 작업이 생성되면 외부 서비스에 불필요한 부하가 발생하고, 클라이언트가 작업을 추적하기 어려워집니다.

**사고 과정:**
중복 요청 방지에는 여러 접근법이 있습니다:

| 패턴 | 목적 | 적합성 |
|---|---|---|
| 디바운싱 (Debouncing) | 연속 호출 중 마지막만 실행 | 프론트엔드 패턴, 서버에서는 부적합 |
| 스로틀링 (Throttling) | 일정 시간 내 최대 호출 수 제한 | 빈도 제어이지 중복 방지가 아님 |
| **멱등성 키 (Idempotency Key)** | 동일 요청의 중복 실행 방지 | 서버 측 중복 방지에 정확히 부합 |

디바운싱과 스로틀링은 호출 빈도를 제어하는 패턴이며, "동일한 의미의 요청이 중복 실행되는 것을 방지"하는 문제를 해결하지 않습니다.
이 시스템에서는 호출 수를 제한하는 빈도 제어(Rate Limiting)와, 한 작업에 대한 중복 실행을 방지하는 중복 실행 방지(Idempotency)를 분리합니다.

**해결법:**
클라이언트로부터 모든 요청에 `X-Idempotency-Key` 헤더를 포함합니다.
이 키와 이미지 url을 식별자로 하여, 식별자가 같은 요청이 단시간 내 들어오는 경우 같은 요청의 중복 전송으로 취급합니다.
멱등성 키는 UUID, Snowflake 등으로 생성할 수 있고, 이 과제에서는 UUID를 활용합니다.

- 동일 키로 재요청 시: 새 작업을 생성하지 않고 기존 작업을 반환합니다.
- DB의 UNIQUE 제약조건이 최종 방어선 역할을 합니다.
- 동시에 동일 키로 요청이 도달한 경우: UNIQUE 제약 위반으로 하나만 INSERT 성공하고, 나머지는 기존 작업을 조회하여 반환합니다.
- 동일 키 + 다른 imageUrl: 409 Conflict를 반환하여 키 오용을 감지합니다.

**이 방식을 선택한 이유:**
멱등성 키는 HTTP API에서 중복 요청 방지의 사실상 표준 패턴이며, DB UNIQUE 제약조건만으로 원자적 중복 검사가 가능하여 별도 분산 락이 불필요합니다.
UUID가 중복될 확률은 극히 낮아서, 낙관적 락을 통해서 구현하여도 성능 상 락의 오버헤드는 적을 것이라 판단했습니다.

**멱등성 키 만료 정책:**

멱등성 키를 영구 보존하면 다음 문제가 발생합니다:
- DB 용량 증가 (무한정 누적)
- 키 재사용 불가 (동일 키 + 다른 imageUrl 요청 시 409 Conflict)

**해결법:**
- 작업 생성 시 `createdAt` 타임스탬프를 기록합니다.
- 멱등성 키 검증 시 24시간 이상 경과한 키는 만료로 간주하여, 새 작업 생성을 허용합니다.
- 만료 시간 설정은 `task.idempotency.expiry-hours`로 외부화되어 운영 환경에 따라 조정 가능합니다.

**멱등성 키 정리 배치:**
- `@Scheduled` 기반 배치 작업으로 종료 상태(COMPLETED, FAILED)이면서 만료 시간이 경과한 레코드를 물리 삭제합니다.
- 기본 실행 주기: 매일 03:00 (`task.idempotency.cleanup-cron`으로 설정 가능)
- PENDING, PROCESSING 상태의 진행 중 작업은 삭제 대상에서 제외됩니다.

이 정책으로 24시간 내 중복 요청은 방지하되, 오래된 키는 재사용 가능하게 하여 DB 용량을 관리합니다.

---

### 3.4 장애 내성 — 외부 서비스 불안정 대응

**문제 인식:**
외부 서비스는 응답 시간과 안정성이 예측 불가능합니다. 500 에러, 429(Rate Limit), 타임아웃이 발생할 수 있으며, 동작 특성이 사전 예고 없이 변경될 수 있습니다.
장애가 내 서버로 전파되면 전체 시스템의 정합성이 훼손됩니다다.

또한 Graceful shutdown을 적용하더라도 서버 밖 영역에서 장애에는 면역이 되어 있지 않습니다.

**사고 과정:**
실패를 두 유형으로 분류합니다:

- **일시적 실패 (Transient):** 500, 429, 타임아웃. 재시도하면 성공할 가능성이 있음.
- **영구적 실패 (Permanent):** 외부 서비스에 연결이 실패(네트워크 이슈와 외부 서 등) 혹은 FAILED를 반환하거나, 재시도 횟수를 소진한 경우. 재시도해도 결과가 동일.

각 유형에 맞는 대응 전략이 필요하며, 외부 서비스가 장시간 장애일 때 내 서버가 불필요한 요청을 계속 보내지 않도록 차단하는 메커니즘도 필요합니다.

**외부 서버 장애 해결법:**
Resilience4j의 3개 모듈과 자체 재시도 로직을 조합합니다:

| 모듈 | 역할 | 핵심 설정 |
|---|---|---|
| **Circuit Breaker** | 연속 실패 시 호출 차단, 외부 서비스에 복구 시간 부여 | 실패율 50% 이상 시 OPEN, 30초 후 HALF_OPEN |
| **Rate Limiter** | 외부 서비스 호출 속도 사전 제한 | 초당 10건 |
| **Bulkhead** | 동시 호출 수 제한, 리소스 격리 | 동시 10건 |
| **자체 재시도 (TaskOrchestrator)** | 일시적 실패에 재시도, 상태 전이와 통합 | 최대 3회, PROCESSING→PENDING 직접 전이 |

적용 순서: `CircuitBreaker → RateLimiter → Bulkhead → 실제 호출`

재시도 로직은 Resilience4j의 Retry 모듈 대신 TaskOrchestrator에서 직접 관리합니다. 이는 재시도 시 DB 상태 전이(PROCESSING→PENDING)와 통합하여, 불필요한 중간 상태(FAILED) 전이 없이 단일 DB 업데이트로 재시도를 처리하기 위함입니다.

**Mock Worker 에러 코드 매핑:**

| HTTP 상태 | 의미 | 실패 유형 | 대응 전략 |
|---|---|---|---|
| 400 | 잘못된 요청 | 영구적 | FAILED 처리, 재시도 불필요 |
| 401 | 인증 실패 | 일시적 | API Key 재발급 후 재시도 |
| 404 | jobId 없음 | 영구적 | PENDING 복귀하여 새 작업 생성 |
| 422 | 검증 실패 (imageUrl 형식 등) | 영구적 | FAILED 처리, 재시도 불필요 |
| 429 | Rate Limit 초과 | 일시적 | Retry with Backoff |
| 500 | 서버 내부 오류 | 일시적 | Retry with Backoff |
| Timeout | 응답 시간 초과 | 일시적 | Retry with Backoff |

**이 방식을 선택한 이유:**
Resilience4j는 Java/Kotlin 생태계에서 가장 널리 사용되는 경량 장애 내성 라이브러리이며, 데코레이터 패턴으로 모듈을 자유롭게 조합할 수 있습니다.
Kotlin Coroutine과의 통합도 resilience4j-kotlin 모듈로 지원됩니다.

---

### 3.5 처리 보장 모델 — At-Least-Once

**문제 인식:**
분산 환경에서 메시지(작업)가 몇 번 처리되는가에 대한 보장 수준을 정의해야 합니다.

**사고 과정:**
세 가지 모델이 존재합니다:

| 모델 | 설명 | 적합성 |
|---|---|---|
| At-Most-Once | 실패해도 재시도 안 함. 유실 가능 | 데이터 유실 위험으로 부적합 |
| **At-Least-Once** | 실패 시 재시도. 중복 처리 가능하지만 유실 없음 |  |
| Exactly-Once | 정확히 한 번. kafka 등 환경에서도 구현 어려움 | 2PC 등 복잡한 프로토콜 필요, 과도 |

**해결법:**
At-Least-Once 모델을 채택합니다.

- 외부 서비스 호출 실패 시 재시도합니다.
- 서버 재시작 시 진행 중 작업을 재확인하고, 필요 시 재시도합니다.
- 중복 호출 가능성이 있지만, 멱등성 키로 클라이언트 관점에서의 중복을 방지합니다.

**근거:**
Exactly-Once는 복잡성이 높고 재시도를 허용하지 않습니다. At-Least-Once + 멱등성 키 조합으로 중복 요청은 무시하되, retry를 통해 안정성을 높입니다.

---

### 3.6 서버 재시작 시 동작 — 장애 복구

**문제 인식:**
서버가 재시작되면 메모리에만 존재하던 비동기 작업의 컨텍스트가 유실됩니다. 진행 중이던 작업이 방치되거나, 이미 완료된 작업을 중복 처리할 수 있습니다.

**사고 과정:**
핵심 원칙은 "상태의 진실(Source of Truth)은 DB"입니다. 모든 작업 상태를 DB에 영속화하면 재시작 후 복구가 가능합니다.
외부 서비스의 불안정성에 대한 대응(Resilience4j, Retry, Circuit Breaker)은 3.4 참조. 이 섹션은 내부 서버 재시작 시 작업 상태 복구에 집중합니다.

복구 시 고려해야 할 케이스:

| DB 상태 | 외부 작업 ID | 실제 상황 | 복구 동작 |
|---|---|---|---|
| PROCESSING | 있음 | 외부 서비스에서 처리 중이거나 완료/실패 | 외부 서비스에 상태 확인 → DB 동기화 |
| PROCESSING | 없음 | 외부 서비스에 전달 전 다운 | PENDING으로 복귀, 재시도 |
| PENDING | - | 비동기 처리 시작 전 다운 | 자연스럽게 재처리 |
| COMPLETED/FAILED | - | 이미 종료 | 복구 불필요 |

**해결법:**
`ApplicationReadyEvent`에서 복구 로직을 실행합니다:

1. PROCESSING + 외부 작업 ID 있음 → 외부 서비스에 상태 확인 API 호출 → DB 동기화
2. PROCESSING + 외부 작업 ID 없음 → PENDING으로 복귀하여 재시도
3. PENDING → 워커가 자연스럽게 재처리

**복구 실패 안전장치:**
복구 과정에서 예외가 발생하면(외부 서비스 호출 실패, submitAsync 실패 등), 작업을 FAILED 상태로 전이하여 누락을 방지합니다. 이전에는 복구 실패 시 submitAsync로 재등록을 시도했으나, submitAsync 자체가 실패하면 작업이 누락될 수 있었습니다.

**정합성이 깨질 수 있는 지점:**
외부 서비스에 POST 요청을 보내 작업 ID를 수신한 후, DB에 작업 ID를 저장하기 전에 서버가 다운되는 경우. 외부 서비스에는 작업이 존재하지만 내 DB에는 기록되지 않아 유실되는 작업 내역이 발생합니다.

발생 확률이 극히 낮으며(수 ms 윈도우), 해당 작업은 PENDING으로 남아있어 재시도 시 새 작업 ID로 정상 처리됩니다.

**Graceful Shutdown:**
서버 애플리케이션 범위에서 갑작스러운 종료에 대응합니다.
SIGTERM 수신 시 새 요청 수신을 거부하고, 진행 중 작업의 완료를 일정 시간(30초) 대기합니다. 미완료 작업은 재시작 시 복구 로직이 처리합니다.

---

### 3.7 트래픽 증가 시 병목 가능 지점

**분석 결과 (우선순위순):**

**1순위: 외부 서비스 처리 용량**
이 시스템의 근본적 병목입니다. 내 서버가 아무리 빨라도 외부 서비스가 처리할 수 있는 속도를 초과할 수 없습니다. Rate Limiter와 Bulkhead로 호출 속도와 동시성을 제한하여 429 발생을 최소화합니다.

**2순위: DB Connection Pool**
코루틴 다수가 동시에 DB 커넥션을 요구하면 풀 고갈이 발생합니다. HikariCP 풀 사이즈를 명시적으로 설정하고(maximum-pool-size: 10, minimum-idle: 5), 커넥션 획득 타임아웃(30초), idle 타임아웃(10분), 커넥션 최대 수명(30분)을 설정하여 풀 관리를 최적화합니다.

**3순위: Polling 부하**
PROCESSING 상태 작업이 N개이면 Polling으로 초당 N/interval건의 GET 요청이 외부 서비스에 발생합니다. Polling 간격을 점진적으로 늘리고(2초 → 10초, multiplier 2.0 + jitter), Semaphore로 동시 Polling 수에 상한(5개)을 두며, 최대 폴링 시간 제한(5분, `withTimeout`)을 적용하여 무한 폴링을 방지합니다.

**4순위: 서블릿 커넥션 한계**
OS 레벨 소켓 한계(file descriptor)에 도달할 수 있습니다. max-connections 조정으로 대응하며, 근본적으로는 수평 확장이 필요합니다.

---

### 3.8 외부 시스템 연동 방식

**HTTP 클라이언트 선택:**

| 선택지 | 장점 | 단점 |
|---|---|---|
| RestTemplate | Spring 기본, 단순 | Deprecated 예정, 블로킹 |
| OpenFeign | 선언적 인터페이스 | synchronized 블록으로 코루틴 환경 부적합 |
| RestClient | Spring 6.1 공식, 동기 스타일 | 코루틴 환경에서 블로킹 |
| **WebClient** | 논블로킹, Coroutine 통합 (awaitBody) | WebFlux 의존성 추가 필요 |

**해결법: WebClient + Coroutine (suspend fun)**

WebClient를 선택한 이유: `awaitBody()` 확장 함수로 Kotlin Coroutine과 자연스럽게 통합되며, 논블로킹 I/O를 수행합니다. WebFlux 전면 도입 없이 WebClient만 HTTP 클라이언트로 사용하고, 나머지는 Spring MVC를 유지합니다.

**Mock Worker API 스펙:**

| 엔드포인트 | 메서드 | 인증 | 요청 | 응답 | 에러 코드 |
|---|---|---|---|---|---|
| `/mock/auth/issue-key` | POST | 불필요 | `{ candidateName, email }` | `{ apiKey }` | 400, 422, 500 |
| `/mock/process` | POST | X-API-KEY | `{ imageUrl }` | `{ jobId }` | 400, 401, 422, 429, 500 |
| `/mock/process/{jobId}` | GET | X-API-KEY | - | `{ jobId, status, result }` | 401, 404, 500 |

**상태 모델 매핑:**
- Mock Worker: `PROCESSING`, `COMPLETED`, `FAILED` (3단계)
- 내부 시스템: `PENDING`, `CANCELLED` 추가 → 5단계 (서버 재시작 정합성을 위해 필수, CANCELLED는 향후 요구사항 대비)

**주요 동작 흐름:**
1. API Key 발급: 서버 시작 시 또는 401 응답 시 자동 재발급
2. 작업 제출: POST /mock/process → `jobId` 수신 → DB에 `PROCESSING` 상태로 저장
3. 상태 폴링: GET /mock/process/{jobId}로 주기적 조회 → `status`와 `result` 동기화
4. 404 처리: jobId 없음 → PENDING으로 복귀하여 재시도

**외부 서비스 API Key 관리 (상세):**

**관리 전략:**
- `@Component` 싱글톤 빈으로 구현하여 애플리케이션 전체에서 단일 인스턴스로 관리합니다.
- API Key는 메모리에만 보존합니다 (DB 영속화 불필요, 재시작 시 재발급).
- `@Volatile var`로 저장하고, 재발급 시 `Mutex`로 동시성을 제어합니다.

**발급 시점:**

1. **서버 시작 시 (ApplicationReadyEvent):**
   - Mock Worker에 API Key 발급 요청 (`POST /mock/auth/issue-key`)
   - 발급 실패 시 에러를 로깅하지만 서버는 정상 기동 (가용성 우선)
   - 첫 작업 요청 시 API Key가 없으면 즉시 재발급 시도

2. **401 응답 시 (런타임 재발급):**
   - Mock Worker 호출 중 `401 Unauthorized` 발생 시
   - 즉시 API Key 재발급 후 원래 요청 재시도
   - 재발급 성공 시 해당 작업은 정상 진행

**동시 재발급 방지 (Race Condition 대응):**

여러 코루틴이 동시에 401을 받는 경우, 하나만 재발급하고 나머지는 대기하도록 합니다.
Kotlin Coroutine의 `Mutex`를 사용하여 suspend 가능한 상호 배제를 구현하고, double-check 패턴으로 불필요한 재발급을 방지합니다:

```kotlin
@Component
class ApiKeyManager(private val webClient: WebClient) {
    @Volatile
    private var _apiKey: String? = null
    private val reissueMutex = Mutex()

    suspend fun reissueApiKey() {
        reissueMutex.withLock {
            // Double-check: 다른 코루틴이 이미 재발급했을 수 있음
            if (_apiKey != null) return

            repeat(MAX_REISSUE_ATTEMPTS) { attempt ->
                try {
                    issueApiKey()
                    return
                } catch (e: Exception) {
                    if (attempt == MAX_REISSUE_ATTEMPTS - 1) throw ApiKeyUnavailableException(...)
                }
            }
        }
    }
}
```

`AtomicBoolean` 대신 `Mutex`를 사용한 이유:
- `AtomicBoolean`의 `compareAndSet`은 lock 획득 실패 시 즉시 반환하여, 동시 호출자가 재발급 결과를 기다리지 않고 빈 키로 실패할 수 있습니다.
- `Mutex.withLock`은 suspend 가능하여 동시 호출자가 코루틴을 양보하며 대기하고, double-check로 이미 재발급된 경우 불필요한 재발급을 건너뜁니다.

**재발급 실패 처리:**
- 최대 3회 재시도 (Exponential Backoff: 1s → 3s → 9s)
- 모든 시도 실패 시 `ApiKeyUnavailableException` 발생
- 해당 작업은 `FAILED` 상태로 전환하고 에러 메시지에 "API Key 발급 실패" 기록
- Circuit Breaker가 OPEN 상태면 재발급 없이 빠르게 실패

**영속화 불필요 이유:**
- API Key는 서버 재시작 시 언제든 재발급 가능 (무상태에 가까운 인증 토큰)
- DB 저장 시 암호화/복호화 오버헤드 증가
- 메모리 관리로 충분하며 보안상 노출 위험 최소화

**이미지 데이터 표현 방식:**
외부 서비스가 이미지 URL(문자열)을 받는 구조이므로, 클라이언트도 이미지 URL을 직접 전달하는 방식을 채택했습니다. 서버가 바이너리 이미지를 수신하여 저장·URL 변환하는 방식 대비, 서버의 메모리/스토리지 부담이 없고 아키텍처가 단순합니다.

## 기술 스택 요약

| 구분 | 기술 | 선택 이유 |
|---|---|---|
| 언어 | Kotlin | JD 요구사항 부합, Coroutine 네이티브 지원 |
| 프레임워크 | Spring Boot 3.x (Spring MVC) | 동기 코드 스타일 유지, 생태계 |
| JDK | 17 | JD 요구사항 부합 |
| 비동기 | Kotlin Coroutine (격리) | 외부 호출만 논블로킹, 전파 범위 제어 |
| HTTP 클라이언트 | WebClient | Coroutine 통합 (awaitBody), 논블로킹 |
| 장애 내성 | Resilience4j (CB, RL, BH) + 자체 재시도 | 경량, 데코레이터 조합, 재시도는 상태 전이와 통합 |
| DB 접근 | Spring Data JPA (Hibernate) | Optimistic Lock, Pageable 내장 |
| DB | MySQL 8.0 | UNIQUE 제약 + Gap Lock으로 원자적 중복 검사 |
| 테스트 | JUnit5, Mockito-Kotlin, WireMock, Testcontainers | Kotlin 친화, HTTP 시뮬레이션, 통합 테스트 |
| 컨테이너 | Docker, Docker Compose | 평가 환경 일관성 |
