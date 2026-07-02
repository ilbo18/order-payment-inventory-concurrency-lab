package com.ilbo18.concurrencylab.payment.presentation;

import com.ilbo18.concurrencylab.payment.application.PaymentApproveCommand;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record PaymentApproveRequest(
        @NotNull
        @Positive
        Long orderId,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount
) {

    public PaymentApproveCommand toCommand() {
        return new PaymentApproveCommand(orderId, amount);
    }
}
