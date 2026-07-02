package com.ilbo18.concurrencylab.common.exception;

import com.ilbo18.concurrencylab.common.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * API에서 발생한 예외를 일관된 JSON 에러 응답으로 변환한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 규칙 위반은 예외가 가진 에러 코드 기준으로 HTTP 상태와 응답 코드를 결정한다.
     */
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException exception) {
        ErrorCode errorCode = exception.getErrorCode();

        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, exception.getMessage()));
    }

    /**
     * Request DTO 검증 실패는 클라이언트가 어떤 필드를 수정해야 하는지 알 수 있도록 필드 오류를 함께 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        List<ErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
                                                              .getFieldErrors()
                                                              .stream()
                                                              .map(fieldError -> new ErrorResponse.FieldError(fieldError.getField(), resolveMessage(fieldError.getDefaultMessage())))
                                                              .toList();

        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, "Invalid request.", fieldErrors));
    }

    /**
     * 도메인 객체 생성 과정의 단순 값 검증 실패를 잘못된 요청으로 변환한다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(ErrorCode.INVALID_REQUEST, exception.getMessage()));
    }

    /**
     * 분류하지 못한 예외는 내부 구현 정보를 숨기고 서버 오류로 응답한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception) {
        return ResponseEntity.internalServerError().body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, "Internal server error."));
    }

    private String resolveMessage(String message) {
        if (message == null || message.isBlank()) {
            return "Invalid value.";
        }
        return message;
    }
}
