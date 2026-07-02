package com.ilbo18.concurrencylab.common.exception;

/**
 * Redis 분산락을 제한 시간 안에 획득하지 못했을 때 주문 흐름을 중단하는 예외이다.
 */
public class RedisLockException extends DomainException {

    public RedisLockException(String lockKey, int retryCount) {
        super(ErrorCode.LOCK_ACQUIRE_FAILED, "Failed to acquire Redis lock. key=" + lockKey + ", retryCount=" + retryCount);
    }

    public RedisLockException(String message, Throwable cause) {
        super(ErrorCode.LOCK_ACQUIRE_FAILED, message, cause);
    }
}
