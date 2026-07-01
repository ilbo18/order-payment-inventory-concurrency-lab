package com.ilbo18.concurrencylab.common.response;

import com.ilbo18.concurrencylab.common.exception.ErrorCode;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors
) {

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<FieldError> fieldErrors) {
        return new ErrorResponse(errorCode.name(), message, fieldErrors);
    }

    public record FieldError(
            String field,
            String message
    ) {
    }
}
