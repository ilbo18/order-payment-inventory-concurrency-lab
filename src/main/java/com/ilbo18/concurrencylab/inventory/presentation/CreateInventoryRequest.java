package com.ilbo18.concurrencylab.inventory.presentation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateInventoryRequest(
        @NotNull
        @Positive
        Long productId,

        @NotNull
        @PositiveOrZero
        Integer quantity
) {
}
