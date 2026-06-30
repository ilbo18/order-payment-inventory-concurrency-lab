# AGENTS.md

## 프로젝트 목표

- 주문/결제/재고 차감 흐름에서 발생하는 동시성 문제를 테스트로 재현한다.
- 비관적 락, 낙관적 락, Redis 분산락, 멱등키를 단계적으로 적용하고 비교한다.
- 단순 CRUD가 아니라 정합성 보장 흐름을 보여주는 실무형 백엔드 프로젝트로 만든다.

## 기술 선택 기준

- Java 21
- Spring Boot 3.x
- Gradle Groovy DSL
- PostgreSQL
- Spring Data JPA
- Flyway
- JUnit5
- Testcontainers
- Redis는 분산락 단계에서 추가한다.

## 패키지 구조 원칙

- 도메인 중심 패키지 구조를 사용한다.
- 각 도메인은 `domain`, `application`, `infrastructure`, `presentation` 하위 패키지를 가진다.
- `domain`에는 핵심 규칙과 상태 변경 로직을 둔다.
- `application`에는 유스케이스와 트랜잭션 경계를 둔다.
- `infrastructure`에는 JPA Repository, 외부 연동 구현체를 둔다.
- `presentation`에는 Controller, Request, Response를 둔다.

## DDD 적용 기준

- 과한 추상화는 하지 않는다.
- 처음부터 Hexagonal Architecture를 강하게 적용하지 않는다.
- Aggregate, Domain Event, Port/Adapter는 필요해지는 시점에만 도입한다.
- Entity에 모든 비즈니스 로직을 몰아넣지 않고, 도메인 규칙과 유스케이스 책임을 구분한다.

## TDD 원칙

- 도메인 규칙은 단위 테스트를 먼저 작성한다.
- 동시성, 트랜잭션, 락 전략은 통합 테스트로 검증한다.
- 실패하는 테스트를 먼저 만들고, 그 다음 구현한다.
- 동시성 테스트에는 `ExecutorService`, `CountDownLatch`를 사용한다.
- 테스트 이름은 어떤 상황에서 어떤 결과를 기대하는지 명확히 작성한다.

## 작업 방식

- 한 번에 큰 기능을 만들지 않는다.
- 1개 작업은 1개 목적만 가진다.
- 구현 전에 영향 범위를 먼저 설명한다.
- 작업 후 변경 파일 목록과 테스트 결과를 요약한다.
- 불필요한 포맷팅 변경을 하지 않는다.
- 새 파일은 필요한 경우에만 만든다.

## 금지 사항

- 처음부터 Kafka, RabbitMQ, MSA를 도입하지 않는다.
- 처음부터 Redis 분산락을 적용하지 않는다.
- H2만으로 동시성 검증을 끝내지 않는다.
- 구현되지 않은 내용을 README에 과장해서 쓰지 않는다.
- 기능과 무관한 리팩토링을 하지 않는다.

## 기본 검증 명령

- `./gradlew test`
- `./gradlew bootJar`
- `docker compose up -d`
