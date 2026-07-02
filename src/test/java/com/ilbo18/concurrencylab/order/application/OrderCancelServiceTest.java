package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderStatus;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.payment.application.PaymentApproveCommand;
import com.ilbo18.concurrencylab.payment.application.PaymentService;
import com.ilbo18.concurrencylab.payment.domain.Payment;
import com.ilbo18.concurrencylab.payment.domain.PaymentStatus;
import com.ilbo18.concurrencylab.payment.infrastructure.gateway.FakePaymentGateway;
import com.ilbo18.concurrencylab.payment.infrastructure.PaymentRepository;
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
@Import({OrderService.class, OrderCancelService.class, PaymentService.class, FakePaymentGateway.class})
class OrderCancelServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderCancelService orderCancelService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private TestEntityManager entityManager;

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void created_order_cancel_changes_order_status_to_canceled() {
        Product product = saveProductWithInventory(10);
        OrderEntity order = createOrder(product.getId(), 3);

        OrderEntity canceledOrder = orderCancelService.cancel(order.getId());

        entityManager.flush();
        entityManager.clear();

        OrderEntity foundOrder = orderRepository.findById(canceledOrder.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void created_order_cancel_restores_decreased_inventory() {
        Product product = saveProductWithInventory(10);
        OrderEntity order = createOrder(product.getId(), 3);

        orderCancelService.cancel(order.getId());

        entityManager.flush();
        entityManager.clear();

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();

        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    void confirmed_order_with_approved_payment_cancel_changes_order_status_to_canceled() {
        Product product = saveProductWithInventory(10);
        OrderEntity order = createOrder(product.getId(), 2);
        paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(20000), "cancel-payment-key-1"));

        OrderEntity canceledOrder = orderCancelService.cancel(order.getId());

        entityManager.flush();
        entityManager.clear();

        OrderEntity foundOrder = orderRepository.findById(canceledOrder.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void confirmed_order_cancel_changes_approved_payment_status_to_canceled() {
        Product product = saveProductWithInventory(10);
        OrderEntity order = createOrder(product.getId(), 2);
        Payment payment = paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(20000), "cancel-payment-key-2"));

        orderCancelService.cancel(order.getId());

        entityManager.flush();
        entityManager.clear();

        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    }

    @Test
    void already_canceled_order_cancel_fails() {
        Product product = saveProductWithInventory(10);
        OrderEntity order = createOrder(product.getId(), 1);
        orderCancelService.cancel(order.getId());

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> orderCancelService.cancel(order.getId()))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_ALREADY_CANCELED));
    }

    @Test
    void missing_order_cancel_fails() {
        assertThatThrownBy(() -> orderCancelService.cancel(999L))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    private Product saveProductWithInventory(int quantity) {
        Product product = productRepository.save(new Product("cancel product", BigDecimal.valueOf(10000)));
        inventoryRepository.save(new Inventory(product.getId(), quantity));
        return product;
    }

    private OrderEntity createOrder(Long productId, int quantity) {
        return orderService.create(new CreateOrderCommand(List.of(
                new CreateOrderCommand.Item(productId, quantity)
        )));
    }
}
