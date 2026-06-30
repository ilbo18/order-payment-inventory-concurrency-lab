package com.ilbo18.concurrencylab.common.exception;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(int quantity, int requestedQuantity) {
        super("Insufficient stock. quantity=" + quantity + ", requestedQuantity=" + requestedQuantity);
    }
}
