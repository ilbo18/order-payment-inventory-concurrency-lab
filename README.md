# Order Payment Inventory Concurrency Lab

주문, 결제, 재고 차감 흐름에서 발생할 수 있는 동시성 문제를 테스트로 재현하고, 단계적으로 정합성 보장 전략을 비교하기 위한 Spring Boot 백엔드 프로젝트입니다.

## 기술 스택

- Java 21
- Spring Boot 3.x
- Gradle Groovy DSL
- PostgreSQL
- Spring Data JPA
- Flyway
- Validation
- Lombok
- JUnit 5
- Testcontainers PostgreSQL
- Docker Compose

## 현재 범위

이번 단계에서는 실행 가능한 Spring Boot 프로젝트 기본 골격만 구성했습니다.
아직 Product, Order, Payment, Inventory 도메인과 API Controller는 구현하지 않았습니다.
Redis 분산락도 이후 단계에서 필요할 때 추가합니다.

## 로컬 실행 준비

PostgreSQL 컨테이너를 실행합니다.

```bash
docker compose up -d
```

애플리케이션을 실행합니다.

```bash
gradle bootRun
```

## 참고 사항

Gradle Wrapper는 로컬 환경에서 별도 생성 예정입니다.

## 검증 명령

```bash
gradle test
gradle bootJar
```
