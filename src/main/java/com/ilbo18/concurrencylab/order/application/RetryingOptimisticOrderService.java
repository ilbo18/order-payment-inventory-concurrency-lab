package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * 낙관적 락 충돌이 발생한 주문 생성을 제한된 횟수만큼 다시 시도하는 유스케이스다.
 */
@Service
@RequiredArgsConstructor
public class RetryingOptimisticOrderService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final long BASE_BACKOFF_MILLIS = 10L;

    private final OptimisticOrderService optimisticOrderService;

    /**
     * 낙관적 락 충돌은 애플리케이션 레벨 재시도 정책으로 보완하고, 재고 부족 같은 비즈니스 실패는 즉시 전파한다.
     */
    public OrderEntity create(CreateOrderCommand command) {
        RuntimeException lastOptimisticLockFailure = null;

        for (int retryCount = 0; retryCount <= MAX_RETRY_COUNT; retryCount++) {
            try {
                // 같은 트랜잭션 안에서 반복하면 version 충돌 상태가 유지되므로, 매 시도마다 별도 서비스의 @Transactional 프록시를 다시 호출한다.
                return optimisticOrderService.create(command);
            } catch (RuntimeException exception) {
                if (hasCustomErrorCode(exception, ErrorCode.INSUFFICIENT_STOCK)) {
                    throw exception;
                }
                if (!isOptimisticLockFailure(exception)) {
                    throw exception;
                }

                lastOptimisticLockFailure = exception;
                if (retryCount == MAX_RETRY_COUNT) {
                    throw exception;
                }
                sleepBeforeRetry(retryCount, exception);
            }
        }

        throw new IllegalStateException("Optimistic lock retry loop ended unexpectedly.", lastOptimisticLockFailure);
    }

    private boolean isOptimisticLockFailure(Throwable failure) {
        return hasCause(failure, OptimisticLockingFailureException.class)
                || hasCause(failure, OptimisticLockException.class);
    }

    private boolean hasCustomErrorCode(Throwable failure, ErrorCode expectedErrorCode) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof CustomException customException && customException.getErrorCode() == expectedErrorCode) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> expectedType) {
        Throwable current = failure;
        while (current != null) {
            if (expectedType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void sleepBeforeRetry(int retryCount, RuntimeException optimisticLockFailure) {
        try {
            Thread.sleep(BASE_BACKOFF_MILLIS * (retryCount + 1));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            optimisticLockFailure.addSuppressed(interruptedException);
            throw optimisticLockFailure;
        }
    }
}
