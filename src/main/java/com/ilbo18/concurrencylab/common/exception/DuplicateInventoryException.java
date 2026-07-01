package com.ilbo18.concurrencylab.common.exception;

public class DuplicateInventoryException extends DomainException {

    public DuplicateInventoryException(Long productId) {
        super(ErrorCode.DUPLICATE_INVENTORY, "Inventory already exists. productId=" + productId);
    }
}
