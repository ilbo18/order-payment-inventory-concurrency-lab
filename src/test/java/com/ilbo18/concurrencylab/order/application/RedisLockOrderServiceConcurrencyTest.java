package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=20")
class RedisLockOrderServiceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(RedisLockOrderServiceConcurrencyTest.class);

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_ORDER_COUNT = 20;
    private static final int ORDER_QUANTITY = 1;
    private static final int REDIS_PORT = 6379;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(REDIS_PORT);

    @Autowired
    private RedisLockOrderService redisLockOrderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }

    @Test
    void Redis_분산락으로_동시_주문_재고_차감_정합성을_보장한다() throws InterruptedException {
        Product product = productRepository.saveAndFlush(new Product("Redis 분산락 주문 상품", BigDecimal.valueOf(10000)));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), INITIAL_STOCK));
        long orderCountBefore = orderRepository.count();

        // 같은 상품의 재고 차감 진입점을 Redis lock key로 보호해 여러 인스턴스 환경의 경합을 재현한다.
        ConcurrencyResult result = executeConcurrentOrders(product.getId());

        Inventory finalInventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        long createdOrderCount = orderRepository.count() - orderCountBefore;
        int finalQuantity = finalInventory.getQuantity();
        int consistencyTotal = result.successCount() + finalQuantity;

        log.info("Redis 분산락 주문 생성 동시성 테스트 결과: success={}, failure={}, finalQuantity={}, createdOrders={}, consistencyTotal={}, failureTypes={}",
                result.successCount(),
                result.failureCount(),
                finalQuantity,
                createdOrderCount,
                consistencyTotal,
                result.failureTypes()
        );

        assertAll(
                () -> assertThat(result.successCount()).as("Redis 분산락에서도 성공 주문 수가 초기 재고를 초과하면 안 된다.")
                                                       .isBetween(1, INITIAL_STOCK),
                () -> assertThat(result.failureCount()).as("동시 주문 요청 중 성공하지 못한 요청은 실패로 집계되어야 한다.")
                                                       .isEqualTo(CONCURRENT_ORDER_COUNT - result.successCount()),
                () -> assertThat(finalQuantity).as("Redis lock으로 보호된 재고 차감 후 최종 재고는 음수가 되면 안 된다.")
                                               .isNotNegative(),
                () -> assertThat(createdOrderCount).as("실제 생성된 주문 수는 성공으로 반환된 주문 수와 같아야 한다.")
                                                   .isEqualTo(result.successCount()),
                () -> assertThat(consistencyTotal).as("성공 주문 수와 최종 재고 합은 초기 재고와 일치해야 한다.")
                                                  .isEqualTo(INITIAL_STOCK),
                () -> assertThat(result.failures()).as("실패는 재고 부족 또는 Redis lock 획득 실패로 설명되어야 한다.")
                                                   .allMatch(this::isExpectedFailure)
        );
    }

    private ConcurrencyResult executeConcurrentOrders(Long productId) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_ORDER_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_ORDER_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(CONCURRENT_ORDER_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < CONCURRENT_ORDER_COUNT; i++) {
                executorService.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        redisLockOrderService.create(new CreateOrderCommand(List.of(new CreateOrderCommand.Item(productId, ORDER_QUANTITY))));
                        successCount.incrementAndGet();
                    } catch (Exception exception) {
                        failureCount.incrementAndGet();
                        failures.add(exception);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).as("동시 주문 요청 스레드가 제한 시간 안에 준비되어야 한다.")
                                                              .isTrue();
            startLatch.countDown();
            assertThat(doneLatch.await(30, TimeUnit.SECONDS)).as("동시 주문 요청이 제한 시간 안에 모두 종료되어야 한다.")
                                                             .isTrue();

            return new ConcurrencyResult(successCount.get(), failureCount.get(), failures);
        } finally {
            executorService.shutdownNow();
        }
    }

    private boolean isExpectedFailure(Throwable failure) {
        return hasCustomErrorCode(failure, ErrorCode.INSUFFICIENT_STOCK)
                || hasCustomErrorCode(failure, ErrorCode.LOCK_ACQUIRE_FAILED);
    }

    private boolean hasCustomErrorCode(Throwable failure, ErrorCode expectedErrorCode) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CustomException customException && customException.getErrorCode() == expectedErrorCode) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private record ConcurrencyResult(int successCount, int failureCount, Queue<Throwable> failures) {
        List<String> failureTypes() {
            return failures.stream()
                           .map(failure -> failure.getClass().getSimpleName())
                           .distinct()
                           .toList();
        }
    }
}
