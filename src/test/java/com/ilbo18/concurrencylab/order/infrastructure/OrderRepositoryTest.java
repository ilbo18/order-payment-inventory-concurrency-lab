package com.ilbo18.concurrencylab.order.infrastructure;

import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderItem;
import com.ilbo18.concurrencylab.order.domain.OrderStatus;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void 주문과_주문_항목을_함께_저장하고_조회할_수_있다() {
        Product firstProduct = productRepository.save(new Product("첫 번째 상품", BigDecimal.valueOf(10000)));
        Product secondProduct = productRepository.save(new Product("두 번째 상품", BigDecimal.valueOf(5000)));
        OrderEntity order = new OrderEntity(List.of(
                new OrderItem(firstProduct.getId(), 2, firstProduct.getPrice()),
                new OrderItem(secondProduct.getId(), 1, secondProduct.getPrice())
        ));

        OrderEntity savedOrder = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        // 주문 항목은 주문과 함께 저장되어야 주문 생성 이후 결제 금액 기준을 다시 조회할 수 있다.
        OrderEntity foundOrder = orderRepository.findById(savedOrder.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(foundOrder.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(25000));
        assertThat(foundOrder.getOrderItems()).hasSize(2);
        assertThat(foundOrder.getOrderItems())
                .extracting(OrderItem::getProductId)
                .containsExactlyInAnyOrder(firstProduct.getId(), secondProduct.getId());
    }
}
