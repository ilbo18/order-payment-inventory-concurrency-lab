package com.ilbo18.concurrencylab.order.application;

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
    void 락이_없으면_동시_주문에서_재고_정합성이_깨질_수_있다() throws InterruptedException {
        Product product = productRepository.saveAndFlush(new Product("동시 주문 상품", BigDecimal.valueOf(10000)));
        inventoryRepository.saveAndFlush(new Inventory(product.getId(), INITIAL_STOCK));
        long orderCountBefore = orderRepository.count();

        // 모든 스레드가 같은 재고를 동시에 읽도록 맞춰 lost update 또는 overselling 상황을 재현한다.
        ConcurrencyResult result = executeConcurrentOrders(product.getId());

        Inventory finalInventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        long createdOrderCount = orderRepository.count() - orderCountBefore;
        int finalQuantity = finalInventory.getQuantity();
        int expectedRemainingQuantity = INITIAL_STOCK - result.successCount();
        int consistencyTotal = result.successCount() + finalQuantity;

        log.info("락 없는 주문 생성 동시성 테스트 결과: success={}, failure={}, finalQuantity={}, createdOrders={}, expectedRemainingQuantity={}, consistencyTotal={}, failureTypes={}",
                result.successCount(),
                result.failureCount(),
                finalQuantity,
                createdOrderCount,
                expectedRemainingQuantity,
                consistencyTotal,
                result.failureTypes()
        );

        // 정상이라면 성공 주문 수만큼 재고가 차감되어야 하며, 현재 락 없는 구조에서 경합이 겹치면 이 불변식이 깨져 테스트가 실패한다.
        assertAll(
                () -> assertThat(createdOrderCount).as("주문 생성 트랜잭션이 성공한 수와 실제 생성된 주문 수는 같아야 한다. success=%d, createdOrders=%d", result.successCount(), createdOrderCount)
                                                   .isEqualTo(result.successCount()),
                () -> assertThat(consistencyTotal).as("초기 재고 %d개에서 quantity=1 주문만 성공했다면 성공 주문 수 + 최종 재고는 %d이어야 한다. success=%d, failure=%d, finalQuantity=%d, createdOrders=%d",
                                INITIAL_STOCK,
                                INITIAL_STOCK,
                                result.successCount(),
                                result.failureCount(),
                                finalQuantity,
                                createdOrderCount)
                                                  .isEqualTo(INITIAL_STOCK)
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

            // 락이 없으므로 스레드 스케줄링과 DB 커넥션 획득 순서에 따라 이 재현 테스트는 간헐적으로 통과할 수 있다.
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
