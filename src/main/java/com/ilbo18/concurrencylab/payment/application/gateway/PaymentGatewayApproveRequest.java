package com.ilbo18.concurrencylab.payment.application.gateway;

import java.math.BigDecimal;

public record PaymentGatewayApproveRequest(
        Long orderId,
        BigDecimal amount,
        String idempotencyKey
) {
}
