package com.ilbo18.concurrencylab.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 실패 응답에 노출할 에러 코드와 HTTP 상태를 정의한다.
 */
public enum ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_INVENTORY(HttpStatus.CONFLICT),
    DUPLICATE_PAYMENT(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST),
    PAYMENT_ALREADY_COMPLETED(HttpStatus.CONFLICT),
    PAYMENT_FAILED(HttpStatus.CONFLICT),
    LOCK_ACQUIRE_FAILED(HttpStatus.CONFLICT),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    DOMAIN_ERROR(HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
