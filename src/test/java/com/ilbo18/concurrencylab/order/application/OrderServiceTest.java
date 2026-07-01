package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.common.exception.InsufficientStockException;
import com.ilbo18.concurrencylab.common.exception.NotFoundException;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderStatus;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(OrderService.class)
class OrderServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void 상품과_재고가_있으면_주문이_생성되고_재고가_차감된다() {
        Product product = saveProduct("주문 상품", BigDecimal.valueOf(10000));
        inventoryRepository.save(new Inventory(product.getId(), 10));

        OrderEntity order = orderService.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(product.getId(), 3)
        )));

        entityManager.flush();
        entityManager.clear();

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        OrderEntity foundOrder = orderService.get(order.getId());

        assertThat(inventory.getQuantity()).isEqualTo(7);
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(foundOrder.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        assertThat(foundOrder.getOrderItems()).hasSize(1);
    }

    @Test
    void 재고가_부족하면_주문_생성이_실패한다() {
        Product product = saveProduct("재고 부족 상품", BigDecimal.valueOf(10000));
        inventoryRepository.save(new Inventory(product.getId(), 1));

        assertThatThrownBy(() -> orderService.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(product.getId(), 2)
        ))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(exception -> assertThat(((InsufficientStockException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    @Test
    void 상품이_없으면_주문_생성이_실패한다() {
        assertThatThrownBy(() -> orderService.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(999L, 1)
        ))))
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(((NotFoundException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    void 재고가_없으면_주문_생성이_실패한다() {
        Product product = saveProduct("재고 미등록 상품", BigDecimal.valueOf(10000));

        assertThatThrownBy(() -> orderService.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(product.getId(), 1)
        ))))
                .isInstanceOf(NotFoundException.class)
                .satisfies(exception -> assertThat(((NotFoundException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVENTORY_NOT_FOUND));
    }

    @Test
    void 주문_단건_조회가_가능하다() {
        Product product = saveProduct("조회 상품", BigDecimal.valueOf(5000));
        inventoryRepository.save(new Inventory(product.getId(), 5));
        OrderEntity order = orderService.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(product.getId(), 2)
        )));

        entityManager.flush();
        entityManager.clear();

        OrderEntity foundOrder = orderService.get(order.getId());

        assertThat(foundOrder.getId()).isEqualTo(order.getId());
        assertThat(foundOrder.getOrderItems()).hasSize(1);
        assertThat(foundOrder.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    private Product saveProduct(String name, BigDecimal price) {
        return productRepository.save(new Product(name, price));
    }
}
