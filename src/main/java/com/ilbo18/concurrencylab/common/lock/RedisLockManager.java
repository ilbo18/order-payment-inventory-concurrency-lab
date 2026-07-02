package com.ilbo18.concurrencylab.common.lock;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis SET NX PX 기반으로 상품 재고 차감 구간에 사용할 분산락을 관리한다.
 */
@Component
@RequiredArgsConstructor
public class RedisLockManager {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final int MAX_ACQUIRE_RETRY_COUNT = 10;
    private static final long ACQUIRE_RETRY_BACKOFF_MILLIS = 50L;
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * 상품별 재고 차감 진입점을 보호하기 위해 productId 기준 lock key를 획득한다.
     */
    public RedisLock acquireInventoryLock(Long productId) {
        return acquire("lock:inventory:" + productId);
    }

    /**
     * TTL이 있는 SET NX로 락을 획득하고, 이미 선점된 경우 짧게 대기한 뒤 제한 횟수만 재시도한다.
     */
    public RedisLock acquire(String lockKey) {
        String lockValue = UUID.randomUUID().toString();

        for (int retryCount = 0; retryCount <= MAX_ACQUIRE_RETRY_COUNT; retryCount++) {
            Boolean acquired = tryAcquire(lockKey, lockValue);
            if (Boolean.TRUE.equals(acquired)) {
                return new RedisLock(lockKey, lockValue);
            }

            if (retryCount == MAX_ACQUIRE_RETRY_COUNT) {
                throw new CustomException(ErrorCode.LOCK_ACQUIRE_FAILED, "Failed to acquire Redis lock. key=" + lockKey + ", retryCount=" + retryCount);
            }
            sleepBeforeRetry(lockKey);
        }

        throw new CustomException(ErrorCode.LOCK_ACQUIRE_FAILED, "Failed to acquire Redis lock. key=" + lockKey + ", retryCount=" + MAX_ACQUIRE_RETRY_COUNT);
    }

    private Boolean tryAcquire(String lockKey, String lockValue) {
        return redisTemplate.execute((RedisCallback<Boolean>) connection -> connection.stringCommands()
                .set(
                        lockKey.getBytes(StandardCharsets.UTF_8),
                        lockValue.getBytes(StandardCharsets.UTF_8),
                        Expiration.milliseconds(LOCK_TTL.toMillis()),
                        RedisStringCommands.SetOption.ifAbsent()
                ));
    }

    /**
     * 다른 요청이 TTL 만료 후 새로 획득한 락을 지우지 않도록, 저장된 value가 내 토큰과 같을 때만 삭제한다.
     */
    public void release(RedisLock redisLock) {
        redisTemplate.execute(RELEASE_SCRIPT, List.of(redisLock.key()), redisLock.value());
    }

    private void sleepBeforeRetry(String lockKey) {
        try {
            Thread.sleep(ACQUIRE_RETRY_BACKOFF_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CustomException(ErrorCode.LOCK_ACQUIRE_FAILED, "Interrupted while waiting for Redis lock. key=" + lockKey, exception);
        }
    }

    public record RedisLock(String key, String value) {
    }
}
