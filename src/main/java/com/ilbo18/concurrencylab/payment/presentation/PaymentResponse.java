package com.ilbo18.concurrencylab.payment.presentation;

import com.ilbo18.concurrencylab.payment.domain.Payment;
import com.ilbo18.concurrencylab.payment.domain.PaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(
        Long paymentId,
        Long orderId,
        BigDecimal amount,
        PaymentStatus status,
        String failureReason
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getFailureReason()
        );
    }
}
