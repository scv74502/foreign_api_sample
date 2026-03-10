# 디자인 패턴

## 도메인 모델
- Entity 내부에 비즈니스 로직 캡슐화 (Rich Domain Model)
- 상태 전이: enum에 `canTransitionTo()`, entity에 `transitionTo()`
- 낙관적 잠금: `@Version`

## 패키지 구조
config / controller / domain / repository / service / client / recovery / exception

## 데이터베이스
- Flyway 마이그레이션 기반 스키마 관리
- JPA `ddl-auto: validate` — 런타임 스키마 자동 변경 금지
- 환경변수 기반 설정 외부화 (.env)

## 테스트
- Testcontainers MySQL 기반 통합 테스트
- 단위 테스트: Spring 컨텍스트 없이 순수 로직 테스트
