package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.InsufficientStockException;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
class OptimisticOrderServiceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(OptimisticOrderServiceConcurrencyTest.class);

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_ORDER_COUNT = 20;
    private static final int ORDER_QUANTITY = 1;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OptimisticOrderService optimisticOrderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void 낙관적_락으로_동시_주문_재고_차감_충돌을_감지한다() throws InterruptedException {
        Product product = productRepository.saveAndFlush(new Product("낙관적 락 주문 상품", BigDecimal.valueOf(10000)));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), INITIAL_STOCK));
        long orderCountBefore = orderRepository.count();

        // 낙관적 락은 충돌을 감지할 뿐 재시도하지 않으므로, 동시에 시작한 주문 중 일부는 version 충돌로 실패할 수 있다.
        ConcurrencyResult result = executeConcurrentOrders(product.getId());

        Inventory finalInventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        long createdOrderCount = orderRepository.count() - orderCountBefore;
        int finalQuantity = finalInventory.getQuantity();
        int consistencyTotal = result.successCount() + finalQuantity;

        log.info("낙관적 락 주문 생성 동시성 테스트 결과: success={}, failure={}, finalQuantity={}, createdOrders={}, consistencyTotal={}, failureTypes={}",
                result.successCount(),
                result.failureCount(),
                finalQuantity,
                createdOrderCount,
                consistencyTotal,
                result.failureTypes()
        );

        assertAll(
                () -> assertThat(result.successCount()).as("재시도가 없는 낙관적 락에서는 성공 주문 수가 초기 재고를 초과하면 안 된다.")
                                                       .isBetween(1, INITIAL_STOCK),
                () -> assertThat(result.failureCount()).as("동시 주문 요청 중 성공하지 못한 요청은 실패로 집계되어야 한다.")
                                                       .isEqualTo(CONCURRENT_ORDER_COUNT - result.successCount()),
                () -> assertThat(finalQuantity).as("낙관적 락 충돌을 감지하면 최종 재고는 음수가 되면 안 된다.")
                                               .isNotNegative(),
                () -> assertThat(createdOrderCount).as("실제 생성된 주문 수는 성공으로 반환된 주문 수와 같아야 한다.")
                                                   .isEqualTo(result.successCount()),
                () -> assertThat(consistencyTotal).as("성공 주문 수와 최종 재고 합은 초기 재고와 일치해야 한다.")
                                                  .isEqualTo(INITIAL_STOCK),
                () -> assertThat(result.failures()).as("실패는 낙관적 락 충돌 또는 재고 부족으로 설명되어야 한다.")
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
                        optimisticOrderService.create(new CreateOrderCommand(List.of(new CreateOrderCommand.Item(productId, ORDER_QUANTITY))));
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
        return hasCause(failure, InsufficientStockException.class)
                || hasCause(failure, ObjectOptimisticLockingFailureException.class)
                || hasCause(failure, OptimisticLockException.class);
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
        Throwable current = failure;
        while (current != null) {
            if (expectedType.isInstance(current)) {
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
