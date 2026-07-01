package com.ilbo18.concurrencylab.common.exception;

public class NotFoundException extends DomainException {

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
