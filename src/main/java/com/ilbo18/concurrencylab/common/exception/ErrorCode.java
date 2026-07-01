package com.ilbo18.concurrencylab.common.exception;

import org.springframework.http.HttpStatus;

/**
 * API 실패 응답에 노출할 에러 코드와 HTTP 상태를 정의한다.
 */
public enum ErrorCode {

    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_INVENTORY(HttpStatus.CONFLICT),
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
