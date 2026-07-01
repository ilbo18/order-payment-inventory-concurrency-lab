package com.ilbo18.concurrencylab.order.presentation;

import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderItem;
import com.ilbo18.concurrencylab.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        Long orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<Item> items
) {

    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getOrderItems().stream()
                                     .map(Item::from)
                                     .toList()
        );
    }

    public record Item(
            Long productId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) {

        static Item from(OrderItem orderItem) {
            return new Item(
                    orderItem.getProductId(),
                    orderItem.getQuantity(),
                    orderItem.getUnitPrice(),
                    orderItem.getLineAmount()
            );
        }
    }
}
