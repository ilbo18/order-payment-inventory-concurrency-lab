package com.ilbo18.concurrencylab.common.exception;

public class InsufficientStockException extends DomainException {

    public InsufficientStockException(int quantity, int requestedQuantity) {
        super(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock. quantity=" + quantity + ", requestedQuantity=" + requestedQuantity);
    }

    public InsufficientStockException(ErrorCode errorCode, int quantity, int requestedQuantity) {
        super(errorCode, "Insufficient stock. quantity=" + quantity + ", requestedQuantity=" + requestedQuantity);
    }
}
