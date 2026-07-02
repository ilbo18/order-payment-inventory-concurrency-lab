package com.ilbo18.concurrencylab.payment.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderItem;
import com.ilbo18.concurrencylab.order.domain.OrderStatus;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.payment.domain.Payment;
import com.ilbo18.concurrencylab.payment.domain.PaymentStatus;
import com.ilbo18.concurrencylab.payment.infrastructure.PaymentRepository;
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
@Import(PaymentService.class)
class PaymentServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PaymentService paymentService;

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
    void 결제_승인_성공_시_APPROVED_결제가_생성된다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));

        Payment payment = paymentService.approve(command(order.getId(), BigDecimal.valueOf(10000), "payment-key-1"));

        entityManager.flush();
        entityManager.clear();

        Payment foundPayment = paymentRepository.findById(payment.getId()).orElseThrow();

        assertThat(foundPayment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(foundPayment.getOrderId()).isEqualTo(order.getId());
        assertThat(foundPayment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        assertThat(foundPayment.getFailureReason()).isNull();
    }

    @Test
    void 결제_승인_성공_시_주문_상태가_CONFIRMED로_변경된다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(15000));

        paymentService.approve(command(order.getId(), BigDecimal.valueOf(15000), "payment-key-2"));

        entityManager.flush();
        entityManager.clear();

        OrderEntity foundOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 주문_금액과_결제_금액이_다르면_결제_승인이_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));

        assertThatThrownBy(() -> paymentService.approve(command(order.getId(), BigDecimal.valueOf(9000), "payment-key-3")))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
    }

    @Test
    void 없는_주문이면_결제_승인이_실패한다() {
        assertThatThrownBy(() -> paymentService.approve(command(999L, BigDecimal.valueOf(10000), "payment-key-4")))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void 이미_결제된_주문이면_결제_승인이_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));
        paymentService.approve(command(order.getId(), BigDecimal.valueOf(10000), "payment-key-5"));

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> paymentService.approve(command(order.getId(), BigDecimal.valueOf(10000), "payment-key-6")))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_PAYMENT));
    }

    @Test
    void 같은_멱등키와_같은_요청이면_기존_결제_결과를_반환한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));
        Payment firstPayment = paymentService.approve(command(order.getId(), new BigDecimal("10000.00"), "same-payment-key"));

        entityManager.flush();
        entityManager.clear();

        Payment secondPayment = paymentService.approve(command(order.getId(), new BigDecimal("10000.0"), "same-payment-key"));

        assertThat(secondPayment.getId()).isEqualTo(firstPayment.getId());
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void 같은_멱등키와_다른_금액이면_충돌로_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));
        paymentService.approve(command(order.getId(), BigDecimal.valueOf(10000), "conflict-amount-key"));

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> paymentService.approve(command(order.getId(), BigDecimal.valueOf(9000), "conflict-amount-key")))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void 같은_멱등키와_다른_주문이면_충돌로_실패한다() {
        OrderEntity firstOrder = saveOrder(BigDecimal.valueOf(10000));
        OrderEntity secondOrder = saveOrder(BigDecimal.valueOf(10000));
        paymentService.approve(command(firstOrder.getId(), BigDecimal.valueOf(10000), "conflict-order-key"));

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> paymentService.approve(command(secondOrder.getId(), BigDecimal.valueOf(10000), "conflict-order-key")))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
    }

    @Test
    void 멱등키가_비어_있으면_결제_승인이_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));

        assertThatThrownBy(() -> paymentService.approve(command(order.getId(), BigDecimal.valueOf(10000), " ")))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_IDEMPOTENCY_KEY));
    }

    @Test
    void 멱등키가_없으면_결제_승인이_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));

        assertThatThrownBy(() -> paymentService.approve(command(order.getId(), BigDecimal.valueOf(10000), null)))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_IDEMPOTENCY_KEY));
    }

    private OrderEntity saveOrder(BigDecimal amount) {
        return orderRepository.saveAndFlush(new OrderEntity(List.of(new OrderItem(1L, 1, amount))));
    }

    private PaymentApproveCommand command(Long orderId, BigDecimal amount, String idempotencyKey) {
        return new PaymentApproveCommand(orderId, amount, idempotencyKey);
    }
}
