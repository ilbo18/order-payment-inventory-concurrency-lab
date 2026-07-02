package com.ilbo18.concurrencylab.payment.application;

import java.math.BigDecimal;

public record PaymentApproveCommand(
        Long orderId,
        BigDecimal amount,
        String idempotencyKey
) {
}
