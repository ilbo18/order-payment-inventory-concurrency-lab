package com.ilbo18.concurrencylab.common.exception;

/**
 * 도메인 규칙 위반을 API 에러 코드와 함께 전달하는 기본 예외이다.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode errorCode;

    public DomainException(String message) {
        this(ErrorCode.DOMAIN_ERROR, message);
    }

    public DomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
