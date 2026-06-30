package com.ilbo18.concurrencylab.product.presentation;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 17, fraction = 2)
        BigDecimal price
) {
}
