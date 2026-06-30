package com.ilbo18.concurrencylab.inventory.presentation;

import com.ilbo18.concurrencylab.inventory.domain.Inventory;

public record InventoryResponse(
        Long id,
        Long productId,
        int quantity
) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.getId(),
                inventory.getProductId(),
                inventory.getQuantity()
        );
    }
}
