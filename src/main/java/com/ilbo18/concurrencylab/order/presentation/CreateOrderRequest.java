package com.ilbo18.concurrencylab.order.presentation;

import com.ilbo18.concurrencylab.order.application.CreateOrderCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty
        @Valid
        List<Item> items
) {

    public CreateOrderCommand toCommand() {
        return new CreateOrderCommand(
                items.stream()
                     .map(item -> new CreateOrderCommand.Item(item.productId(), item.quantity()))
                     .toList()
        );
    }

    public record Item(
            @NotNull
            @Positive
            Long productId,

            @NotNull
            @Positive
            Integer quantity
    ) {
    }
}
