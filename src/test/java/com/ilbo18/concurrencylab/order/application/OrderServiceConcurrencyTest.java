package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.InsufficientStockException;
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
class OrderServiceConcurrencyTest {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceConcurrencyTest.class);

    private static final int INITIAL_STOCK = 10;
    private static final int CONCURRENT_ORDER_COUNT = 20;
    private static final int ORDER_QUANTITY = 1;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void 비관적_락으로_동시_주문_재고_차감_정합성을_보장한다() throws InterruptedException {
        Product product = productRepository.saveAndFlush(new Product("동시 주문 상품", BigDecimal.valueOf(10000)));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), INITIAL_STOCK));
        long orderCountBefore = orderRepository.count();

        // 20개 주문이 같은 재고 행을 경합하며, 비관적 락이 없으면 lost update 또는 overselling이 발생할 수 있다.
        ConcurrencyResult result = executeConcurrentOrders(product.getId());

        Inventory finalInventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        long createdOrderCount = orderRepository.count() - orderCountBefore;
        int finalQuantity = finalInventory.getQuantity();
        int expectedRemainingQuantity = INITIAL_STOCK - result.successCount();
        int consistencyTotal = result.successCount() + finalQuantity;

        log.info("비관적 락 주문 생성 동시성 테스트 결과: success={}, failure={}, finalQuantity={}, createdOrders={}, expectedRemainingQuantity={}, consistencyTotal={}, failureTypes={}",
                result.successCount(),
                result.failureCount(),
                finalQuantity,
                createdOrderCount,
                expectedRemainingQuantity,
                consistencyTotal,
                result.failureTypes()
        );

        // 비관적 락은 같은 상품 재고 차감을 직렬화하므로 성공 주문 수와 최종 재고의 합이 초기 재고와 일치해야 한다.
        assertAll(
                () -> assertThat(result.successCount()).as("초기 재고 10개만큼만 주문이 성공해야 한다.")
                                                       .isEqualTo(INITIAL_STOCK),
                () -> assertThat(result.failureCount()).as("초기 재고를 초과한 주문은 재고 부족으로 실패해야 한다.")
                                                       .isEqualTo(CONCURRENT_ORDER_COUNT - INITIAL_STOCK),
                () -> assertThat(finalQuantity).as("비관적 락 적용 후 최종 재고는 0이어야 한다.")
                                               .isZero(),
                () -> assertThat(createdOrderCount).as("주문 생성 트랜잭션이 성공한 수와 실제 생성된 주문 수는 같아야 한다. success=%d, createdOrders=%d", result.successCount(), createdOrderCount)
                                                   .isEqualTo(result.successCount()),
                () -> assertThat(consistencyTotal).as("초기 재고 %d개에서 quantity=1 주문만 성공했다면 성공 주문 수 + 최종 재고는 %d이어야 한다. success=%d, failure=%d, finalQuantity=%d, createdOrders=%d",
                                INITIAL_STOCK,
                                INITIAL_STOCK,
                                result.successCount(),
                                result.failureCount(),
                                finalQuantity,
                                createdOrderCount)
                                                  .isEqualTo(INITIAL_STOCK),
                () -> assertThat(result.failures()).as("초기 재고를 초과한 주문은 자연스럽게 재고 부족 예외로 실패해야 한다.")
                                                   .allMatch(InsufficientStockException.class::isInstance)
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
                        orderService.create(new CreateOrderCommand(List.of(new CreateOrderCommand.Item(productId, ORDER_QUANTITY))));
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

            // 비관적 락이 없다면 스레드 스케줄링과 DB 커넥션 획득 순서에 따라 이 검증은 불안정하게 깨질 수 있다.
            return new ConcurrencyResult(successCount.get(), failureCount.get(), failures);
        } finally {
            executorService.shutdownNow();
        }
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
