package com.ilbo18.concurrencylab.common.exception;

/**
 * 도메인과 애플리케이션 규칙 위반을 ErrorCode와 함께 전달하는 공통 예외이다.
 */
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        this(errorCode, errorCode.name());
    }

    public CustomException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CustomException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
