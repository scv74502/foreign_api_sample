# 디자인 패턴

## 코드 주석 원칙
- **주석 지양:** 메소드명, 변수명, 코드 구조만으로 의도를 명확히 전달
- **주석 작성 대상:** 코드만으로 이해 불가능한 비즈니스 로직, 외부 시스템 제약사항, 성능 최적화 등
- **Entity 컬럼 설명:** KDoc 대신 `@Column(comment = "...")` 사용하여 DB 스키마에 설명 반영
- **클래스 KDoc:** Entity는 테이블 설명만 간결히 작성

## 도메인 모델
- POJO/Entity 분리 패턴
  - **POJO (Task):** 순수 비즈니스 로직 (상태 전이 등), JPA 의존 없음
  - **Entity (TaskEntity):** JPA 매핑 전용, 비즈니스 로직 없음. 클래스 KDoc에 테이블 설명 작성. 컬럼 설명은 `@Column(comment = "...")` 사용
  - **Mapper (TaskMapper):** POJO ↔ Entity 변환, stateless object. `entity/mapper/` 디렉토리에 위치
- 상태 전이: enum에 `canTransitionTo()`, POJO에 `transitionTo()`
- 낙관적 잠금: Entity에 `@Version`
- 의존 방향: `entity → domain` (단방향), domain 패키지는 entity를 참조하지 않음

## Enum 매핑
- DB에는 TINYINT로 저장 (`columnDefinition = "TINYINT"`)
- enum에 `code: Int` 프로퍼티 부여
- `companion object`에 `fromCode(code: Int)` 팩토리 메서드 제공
- Entity는 `Int` 필드로 저장, Mapper에서 enum 변환

## 시간 처리
- 시간 타입: `Instant` 사용 (타임존 독립적)
- JVM 타임존: `main()`에서 `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` 설정

## 파일 구조
- 1파일 1클래스: 파일 하나에 class/interface/enum 하나만 선언
- 파일명 = 클래스명

## 패키지 구조
도메인별로 레이어를 가지는 구조:
```
org.sampletask.foreign_api_sample/
    ForeignApiSampleApplication.kt       # 루트: 앱 진입점
    config/                               # 공통 설정
    <도메인>/                              # 도메인별 패키지 (예: task)
        domain/                           # POJO, VO, enum (순수 도메인, JPA 의존 없음)
        entity/                           # JPA Entity (DB 매핑 전용)
            mapper/                       # Entity-Domain 변환 Mapper
        repository/                       # JPA 리포지토리
        service/                          # 비즈니스 로직
        controller/                       # REST 컨트롤러 (API 인터페이스 + 구현)
        client/                           # 외부 API 클라이언트
        exception/                        # 도메인 예외
```

## API 우선 개발
- springdoc 어노테이션으로 API 인터페이스 정의 먼저 작성
- `@Operation`, `@ApiResponse`, `@Schema` 등으로 문서화
- API 인터페이스 정의 후 컨트롤러 구현

## 데이터베이스
- Flyway 마이그레이션 기반 스키마 관리
- JPA `ddl-auto: validate` — 런타임 스키마 자동 변경 금지
- 환경변수 기반 설정 외부화 (.env)

## 테스트
- Testcontainers MySQL 기반 통합 테스트
- 단위 테스트: Spring 컨텍스트 없이 순수 로직 테스트
