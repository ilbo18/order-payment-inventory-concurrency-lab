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

        Payment payment = paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(10000)));

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

        paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(15000)));

        entityManager.flush();
        entityManager.clear();

        OrderEntity foundOrder = orderRepository.findById(order.getId()).orElseThrow();

        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 주문_금액과_결제_금액이_다르면_결제_승인이_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));

        assertThatThrownBy(() -> paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(9000))))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH));
    }

    @Test
    void 없는_주문이면_결제_승인이_실패한다() {
        assertThatThrownBy(() -> paymentService.approve(new PaymentApproveCommand(999L, BigDecimal.valueOf(10000))))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.ORDER_NOT_FOUND));
    }

    @Test
    void 이미_결제된_주문이면_결제_승인이_실패한다() {
        OrderEntity order = saveOrder(BigDecimal.valueOf(10000));
        paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(10000)));

        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> paymentService.approve(new PaymentApproveCommand(order.getId(), BigDecimal.valueOf(10000))))
                .isInstanceOf(CustomException.class)
                .satisfies(exception -> assertThat(((CustomException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_PAYMENT));
    }

    private OrderEntity saveOrder(BigDecimal amount) {
        return orderRepository.saveAndFlush(new OrderEntity(List.of(new OrderItem(1L, 1, amount))));
    }
}
