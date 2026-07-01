package com.ilbo18.concurrencylab.order.application;

import java.util.List;

public record CreateOrderCommand(
        List<Item> items
) {

    public record Item(
            Long productId,
            int quantity
    ) {
    }
}
