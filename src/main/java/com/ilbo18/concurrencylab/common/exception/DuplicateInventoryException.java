package com.ilbo18.concurrencylab.common.exception;

public class DuplicateInventoryException extends DomainException {

    public DuplicateInventoryException(Long productId) {
        super("Inventory already exists. productId=" + productId);
    }
}
