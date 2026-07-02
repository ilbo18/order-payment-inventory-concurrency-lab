package com.ilbo18.concurrencylab.payment.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.payment.application.gateway.PaymentGateway;
import com.ilbo18.concurrencylab.payment.application.gateway.PaymentGatewayApproveRequest;
import com.ilbo18.concurrencylab.payment.application.gateway.PaymentGatewayResult;
import com.ilbo18.concurrencylab.payment.domain.Payment;
import com.ilbo18.concurrencylab.payment.infrastructure.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 주문 금액 검증과 PaymentGateway 승인 결과를 기준으로 내부 결제 상태를 확정한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    /**
     * 멱등키를 검증한 뒤 신규 요청에 대해서만 PaymentGateway를 호출하고 승인 또는 실패 결과를 저장한다.
     */
    @Transactional
    public Payment approve(PaymentApproveCommand command) {
        validateCommand(command);
        String requestHash = calculateRequestHash(command);
        Payment existingPayment = paymentRepository.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (existingPayment != null) {
            return resolveIdempotentPayment(existingPayment, requestHash);
        }

        OrderEntity order = getOrder(command.orderId());
        validatePaymentNotExists(command.orderId());
        validateAmountMatches(order, command.amount());

        Payment payment = Payment.ready(order.getId(), command.amount(), command.idempotencyKey(), requestHash);
        PaymentGatewayResult gatewayResult = paymentGateway.approve(new PaymentGatewayApproveRequest(order.getId(), command.amount(), command.idempotencyKey()));
        applyGatewayResult(payment, order, gatewayResult);

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
        validateIdempotencyKey(command.idempotencyKey());
    }

    private Payment resolveIdempotentPayment(Payment existingPayment, String requestHash) {
        if (!existingPayment.getRequestHash().equals(requestHash)) {
            throw new CustomException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT, "Idempotency-Key already used with different payment request. paymentId=" + existingPayment.getId());
        }
        return existingPayment;
    }

    private String calculateRequestHash(PaymentApproveCommand command) {
        String normalizedAmount = command.amount().stripTrailingZeros().toPlainString();
        String requestSource = command.orderId() + "|" + normalizedAmount;
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(requestSource.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", exception);
        }
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

    private void applyGatewayResult(Payment payment, OrderEntity order, PaymentGatewayResult gatewayResult) {
        if (gatewayResult.success()) {
            payment.approve();
            order.confirm();
            return;
        }

        // 실패 결제도 저장해야 같은 멱등키 재요청 시 새 PG 호출 없이 기존 실패 결과를 반환할 수 있다.
        payment.fail(resolveFailureReason(gatewayResult));
    }

    private String resolveFailureReason(PaymentGatewayResult gatewayResult) {
        if (gatewayResult.failureReason() == null || gatewayResult.failureReason().isBlank()) {
            return "Payment gateway approval failed.";
        }
        return gatewayResult.failureReason();
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

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key must not be blank.");
        }
        if (idempotencyKey.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key must be 100 characters or fewer.");
        }
    }
}
