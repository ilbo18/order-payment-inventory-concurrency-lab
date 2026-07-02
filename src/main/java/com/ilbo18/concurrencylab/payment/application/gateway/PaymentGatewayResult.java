package com.ilbo18.concurrencylab.payment.application.gateway;

public record PaymentGatewayResult(
        boolean success,
        String failureReason,
        String transactionId
) {

    public static PaymentGatewayResult success(String transactionId) {
        return new PaymentGatewayResult(true, null, transactionId);
    }

    public static PaymentGatewayResult failure(String failureReason) {
        return new PaymentGatewayResult(false, failureReason, null);
    }
}
