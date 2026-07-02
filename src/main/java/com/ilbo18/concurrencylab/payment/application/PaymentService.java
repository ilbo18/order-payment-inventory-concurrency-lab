package com.ilbo18.concurrencylab.payment.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.payment.domain.Payment;
import com.ilbo18.concurrencylab.payment.infrastructure.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 외부 PG 연동 없이 주문 금액 검증과 내부 결제 승인 트랜잭션을 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    /**
     * 주문 금액과 결제 요청 금액이 일치하면 결제를 승인하고 주문 상태를 CONFIRMED로 확정한다.
     */
    @Transactional
    public Payment approve(PaymentApproveCommand command) {
        validateCommand(command);
        OrderEntity order = getOrder(command.orderId());
        validatePaymentNotExists(command.orderId());
        validateAmountMatches(order, command.amount());

        Payment payment = Payment.ready(order.getId(), command.amount());
        payment.approve();
        order.confirm();

        return paymentRepository.save(payment);
    }

    /**
     * 결제 ID 기준으로 결제 승인 결과를 조회한다.
     */
    public Payment get(Long paymentId) {
        validatePaymentId(paymentId);
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found. paymentId=" + paymentId));
    }

    /**
     * 주문 ID 기준으로 연결된 결제 승인 결과를 조회한다.
     */
    public Payment getByOrderId(Long orderId) {
        validateOrderId(orderId);
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found. orderId=" + orderId));
    }

    private void validateCommand(PaymentApproveCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Payment approve command must not be null.");
        }
        validateOrderId(command.orderId());
        validateAmount(command.amount());
    }

    private OrderEntity getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND, "Order not found. orderId=" + orderId));
    }

    private void validatePaymentNotExists(Long orderId) {
        if (paymentRepository.existsByOrderId(orderId)) {
            throw new CustomException(ErrorCode.DUPLICATE_PAYMENT, "Payment already exists. orderId=" + orderId);
        }
    }

    private void validateAmountMatches(OrderEntity order, BigDecimal requestedAmount) {
        if (order.getTotalAmount().compareTo(requestedAmount) != 0) {
            throw new CustomException(
                    ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "Payment amount mismatch. orderId=" + order.getId() + ", orderAmount=" + order.getTotalAmount() + ", requestedAmount=" + requestedAmount
            );
        }
    }

    private void validatePaymentId(Long paymentId) {
        if (paymentId == null || paymentId <= 0) {
            throw new CustomException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found. paymentId=" + paymentId);
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CustomException(ErrorCode.ORDER_NOT_FOUND, "Order not found. orderId=" + orderId);
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Payment amount must be greater than or equal to 0.");
        }
    }
}
