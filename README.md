# Order Payment Inventory Concurrency Lab

주문 생성, 재고 차감 과정에서 발생할 수 있는 동시성 문제를 테스트로 재현하고, 데이터 정합성을 보장하는 방법을 단계적으로 검증하는 Spring Boot 백엔드 프로젝트입니다.

단순 CRUD 구현이 아니라 다음 주제를 코드와 테스트로 확인하는 것을 목표로 합니다.

- 주문 생성 시 재고 차감 흐름의 트랜잭션 경계
- 동시에 같은 상품 재고를 차감할 때 발생할 수 있는 lost update, overselling 문제
- PostgreSQL row-level lock 기반의 비관적 락 적용 방식
- JPA `@Version` 기반의 낙관적 락 충돌 감지 방식
- 낙관적 락 충돌 발생 시 제한된 횟수만큼 재시도하는 방식
- 향후 Redis 분산락, 멱등키를 비교하기 위한 기반 구조

## 기술 스택

- Java 21
- Spring Boot 3.x
- Spring Data JPA
- PostgreSQL
- Flyway
- Testcontainers
- JUnit5
- AssertJ
- Docker Compose
- Gradle Groovy DSL

## 핵심 도메인

- Product: 상품 정보 등록과 조회
- Inventory: 상품별 재고 등록, 조회, 차감
- Order: 주문 생성, 주문 항목 관리, 주문 조회
- Common: 공통 예외, 에러 응답 처리

## 주요 기능

- 상품 등록/조회
- 재고 등록/조회
- 주문 생성/조회
- 주문 생성 시 재고 차감
- 재고 부족 시 주문 실패
- 전역 예외 응답 처리
- 비관적 락 기반 재고 차감 정합성 보장
- 낙관적 락 기반 주문 생성 흐름과 동시성 충돌 감지 테스트
- 낙관적 락 충돌 재시도 기반 주문 생성 흐름과 동시성 테스트

## 동시성 문제

예를 들어 초기 재고가 10개인 상품에 대해 동시에 20개의 주문 요청이 들어오고, 각 주문이 1개씩 구매한다고 가정합니다.

락 없이 같은 재고 row를 동시에 읽고 차감하면 여러 트랜잭션이 동일한 재고 값을 기준으로 계산할 수 있습니다. 이 경우 일부 차감 결과가 덮어써지는 lost update가 발생하거나, 실제 재고보다 많은 주문이 성공하는 overselling 문제가 발생할 수 있습니다.

이 프로젝트에서는 주문 생성 트랜잭션 안에서 Inventory row를 조회하는 시점에 `PESSIMISTIC_WRITE` 락을 획득합니다. 같은 상품의 재고 차감 요청은 동일한 Inventory row를 대상으로 경합하므로, PostgreSQL의 `SELECT FOR UPDATE` 기반 row-level lock에 의해 순차 처리됩니다.

## 비관적 락 적용 방식

`InventoryRepository.findByProductIdForUpdate`에서 `@Lock(LockModeType.PESSIMISTIC_WRITE)`를 사용합니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select inventory from Inventory inventory where inventory.productId = :productId")
Optional<Inventory> findByProductIdForUpdate(@Param("productId") Long productId);
```

`OrderService.create`는 하나의 트랜잭션 안에서 상품과 재고를 확인하고, 비관적 락이 걸린 Inventory row를 가져온 뒤 재고를 차감합니다.

```java
@Transactional
public OrderEntity create(CreateOrderCommand command) {
    validateCommand(command);
    List<OrderItem> orderItems = command.items()
            .stream()
            .map(this::createOrderItem)
            .toList();

    return orderRepository.save(new OrderEntity(orderItems));
}
```

재고 조회와 차감은 같은 트랜잭션 안에서 수행됩니다.

```java
private Inventory getInventory(Long productId) {
    return inventoryRepository.findByProductIdForUpdate(productId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found. productId=" + productId));
}
```

## 낙관적 락 적용 방식

`Inventory` 엔티티에는 JPA `@Version` 필드가 있습니다. 여러 트랜잭션이 같은 Inventory row를 읽은 뒤 동시에 수정하면, 먼저 커밋된 트랜잭션이 version을 증가시키고 이후 커밋을 시도하는 트랜잭션은 version 불일치로 실패합니다.

```java
@Version
@Column(nullable = false)
private Long version;
```

낙관적 락 흐름은 기존 `OrderService.create`를 변경하지 않고 `OptimisticOrderService.create`로 분리했습니다. `InventoryRepository.findByProductIdForOptimistic`은 별도 `@Lock` 없이 일반 조회를 수행하고, 충돌 감지는 flush/commit 시점의 version 비교에 맡깁니다.

```java
@Query("select inventory from Inventory inventory where inventory.productId = :productId")
Optional<Inventory> findByProductIdForOptimistic(@Param("productId") Long productId);
```

기본 낙관적 락 흐름은 충돌 감지만 수행하며, 충돌 발생 시 재시도하지 않습니다.

## 낙관적 락 재시도 적용 방식

`RetryingOptimisticOrderService`는 `OptimisticOrderService.create`를 호출하다가 낙관적 락 충돌이 발생하면 제한된 횟수만큼 다시 시도합니다. 재고 부족은 충돌이 아니라 비즈니스 실패이므로 재시도하지 않고 즉시 예외를 전파합니다.

재시도 서비스 자체에는 `@Transactional`을 적용하지 않습니다. 매 시도마다 `OptimisticOrderService.create`의 `@Transactional` 프록시를 다시 호출해야 이전 시도의 영속성 컨텍스트와 version 충돌 상태가 다음 시도에 섞이지 않습니다.

```java
public OrderEntity create(CreateOrderCommand command) {
    for (int retryCount = 0; retryCount <= MAX_RETRY_COUNT; retryCount++) {
        try {
            return optimisticOrderService.create(command);
        } catch (RuntimeException exception) {
            // 낙관적 락 충돌만 재시도하고, 재고 부족은 즉시 실패시킨다.
        }
    }
}
```

## 락 전략 비교

| 방식 | 동작 방식 | 현재 구현 상태 |
| --- | --- | --- |
| 비관적 락 | Inventory row 조회 시점에 `PESSIMISTIC_WRITE`로 잠그고 같은 상품 재고 차감을 직렬화 | 구현 완료 |
| 낙관적 락 | row를 미리 잠그지 않고 `@Version`으로 커밋 시점 충돌 감지 | 구현 완료 |
| 낙관적 락 + 재시도 | 낙관적 락 충돌 시 새 트랜잭션으로 제한 횟수 재시도 | 구현 완료 |

## 비관적 락 동시성 테스트 시나리오

`OrderServiceConcurrencyTest`는 같은 상품에 대한 동시 주문 경합을 재현합니다.

- 초기 재고: 10
- 동시 주문 요청: 20
- 주문 수량: 각 1개
- 기대 결과:
  - 성공 주문 10건
  - 실패 주문 10건
  - 최종 재고 0
  - 성공 주문 수 + 최종 재고 = 초기 재고
  - 실패 주문은 `InsufficientStockException`

테스트는 `ExecutorService`와 `CountDownLatch`를 사용해 여러 주문 요청이 동시에 시작되도록 구성되어 있습니다.

## 낙관적 락 동시성 테스트 시나리오

`OptimisticOrderServiceConcurrencyTest`는 재시도 없는 낙관적 락 주문 생성 흐름에서 같은 상품 재고를 동시에 차감하는 상황을 재현합니다.

- 초기 재고: 10
- 동시 주문 요청: 20
- 주문 수량: 각 1개
- 기대 결과:
  - 성공 주문 수는 초기 재고 이하
  - 최종 재고는 음수가 아님
  - 생성된 주문 수 = 성공 주문 수
  - 성공 주문 수 + 최종 재고 = 초기 재고
  - 실패 원인은 낙관적 락 충돌 또는 재고 부족일 수 있음

낙관적 락은 DB row를 미리 잠그지 않고 충돌을 감지하는 방식입니다. 따라서 충돌 시 재시도하지 않으면 동시에 들어온 주문 중 일부는 실패할 수 있습니다.

## 재시도 낙관적 락 동시성 테스트 시나리오

`RetryingOptimisticOrderServiceConcurrencyTest`는 낙관적 락 충돌이 발생했을 때 제한된 횟수만큼 새 트랜잭션으로 다시 주문 생성을 시도하는 상황을 재현합니다.

- 초기 재고: 10
- 동시 주문 요청: 20
- 주문 수량: 각 1개
- 기대 결과:
  - 성공 주문 수는 초기 재고 이하
  - 최종 재고는 음수가 아님
  - 생성된 주문 수 = 성공 주문 수
  - 성공 주문 수 + 최종 재고 = 초기 재고
  - 실패 원인은 재고 부족 또는 재시도 소진 후 낙관적 락 충돌일 수 있음

재시도 횟수가 충분하면 성공 주문 수가 초기 재고에 가까워질 수 있지만, 테스트는 스레드 스케줄링에 과하게 의존하지 않도록 재고 정합성 불변식을 중심으로 검증합니다.

현재 로컬 Windows 환경에서는 Testcontainers가 Docker를 감지하지 못하는 실행 환경 이슈가 남아 있습니다. 따라서 동시성 테스트 코드는 작성되어 있지만, 해당 환경에서는 Docker/Testcontainers 실행 조건을 먼저 점검해야 합니다.

## 현재 검증 상태

현재 확인된 내용은 다음과 같습니다.

- `compileJava` 성공
- `compileTestJava` 성공
- Spring Boot 애플리케이션 실행 확인
- Docker Compose PostgreSQL 연결 확인

Testcontainers 기반 전체 테스트가 현재 로컬 Windows 환경에서 성공했다고 보지는 않습니다.

## 실행 방법

PostgreSQL을 Docker Compose로 실행합니다.

```bash
docker compose up -d
```

Spring Boot 애플리케이션을 실행합니다.

```bash
./gradlew bootRun
```

전체 빌드는 다음 명령으로 실행합니다.

```bash
./gradlew clean build
```

`clean build`는 테스트를 포함하므로, 현재 로컬 Windows 환경처럼 Testcontainers가 Docker를 감지하지 못하는 경우 실패할 수 있습니다.

동시성 테스트만 단독 실행하려면 다음 명령을 사용합니다.

```bash
./gradlew test --tests "com.ilbo18.concurrencylab.order.application.OrderServiceConcurrencyTest"
```

낙관적 락 동시성 테스트만 단독 실행하려면 다음 명령을 사용합니다.

```bash
./gradlew test --tests "com.ilbo18.concurrencylab.order.application.OptimisticOrderServiceConcurrencyTest"
```

재시도 낙관적 락 동시성 테스트만 단독 실행하려면 다음 명령을 사용합니다.

```bash
./gradlew test --tests "com.ilbo18.concurrencylab.order.application.RetryingOptimisticOrderServiceConcurrencyTest"
```

Windows PowerShell에서는 필요에 따라 `./gradlew` 대신 `.\gradlew.bat`을 사용할 수 있습니다.

## API 예시

기본 실행 주소는 `http://localhost:8080`입니다.

### 상품 등록

```bash
curl -X POST "http://localhost:8080/api/products" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "동시성 테스트 상품",
    "price": 10000.00
  }'
```

### 상품 조회

```bash
curl -X GET "http://localhost:8080/api/products/1"
```

### 재고 등록

```bash
curl -X POST "http://localhost:8080/api/inventories" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "quantity": 10
  }'
```

### 재고 조회

```bash
curl -X GET "http://localhost:8080/api/inventories/products/1"
```

### 주문 생성

```bash
curl -X POST "http://localhost:8080/api/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "items": [
      {
        "productId": 1,
        "quantity": 1
      }
    ]
  }'
```

### 주문 조회

```bash
curl -X GET "http://localhost:8080/api/orders/1"
```

## 현재 구현 범위

구현 완료:

- Product 등록/조회
- Inventory 등록/조회
- Order 생성/조회
- 주문 생성 시 재고 차감
- 재고 부족 예외 처리
- 공통 예외 응답 처리
- PostgreSQL Flyway migration
- 비관적 락 기반 재고 차감
- 낙관적 락 기반 주문 생성 서비스
- 낙관적 락 충돌 재시도 기반 주문 생성 서비스
- 비관적 락/낙관적 락/재시도 낙관적 락 주문 생성 동시성 테스트 코드

미구현:

- Payment
- Redis 분산락 비교
- 주문 취소 시 재고 복구
- 결제 멱등키

## 향후 개선 계획

- 낙관적 락 재시도 횟수와 백오프 정책 설정화
- Redis 분산락 기반 재고 차감 방식 추가
- Payment 승인/실패 흐름 추가
- 주문 취소와 재고 복구 보상 처리 추가
- 멱등키 기반 중복 주문/중복 결제 방지 추가
- Testcontainers 테스트 실행 환경 안정화
