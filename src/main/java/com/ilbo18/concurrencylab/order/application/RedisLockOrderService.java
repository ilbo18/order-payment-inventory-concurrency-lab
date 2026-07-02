package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.common.exception.NotFoundException;
import com.ilbo18.concurrencylab.common.lock.RedisLockManager;
import com.ilbo18.concurrencylab.common.lock.RedisLockManager.RedisLock;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 상품별 Redis lock key로 주문 생성 진입 구간을 보호하는 분산락 주문 유스케이스다.
 */
@Service
@RequiredArgsConstructor
public class RedisLockOrderService {

    private final RedisLockManager redisLockManager;
    private final RedisLockOrderTransactionService redisLockOrderTransactionService;

    /**
     * 여러 애플리케이션 인스턴스가 같은 상품 재고를 차감하지 못하도록 productId 기준 Redis lock을 먼저 획득한다.
     */
    public OrderEntity create(CreateOrderCommand command) {
        List<Long> productIds = collectLockProductIds(command);
        List<RedisLock> redisLocks = new ArrayList<>();
        RuntimeException orderFailure = null;

        try {
            productIds.forEach(productId -> redisLocks.add(redisLockManager.acquireInventoryLock(productId)));
            return redisLockOrderTransactionService.create(command);
        } catch (RuntimeException exception) {
            orderFailure = exception;
            throw exception;
        } finally {
            RuntimeException releaseFailure = releaseLocks(redisLocks);
            if (releaseFailure != null) {
                if (orderFailure != null) {
                    orderFailure.addSuppressed(releaseFailure);
                } else {
                    throw releaseFailure;
                }
            }
        }
    }

    private List<Long> collectLockProductIds(CreateOrderCommand command) {
        validateCommand(command);
        return command.items()
                .stream()
                .map(item -> {
                    validateItem(item);
                    validateProductId(item.productId());
                    return item.productId();
                })
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private RuntimeException releaseLocks(List<RedisLock> redisLocks) {
        RuntimeException releaseFailure = null;

        for (int index = redisLocks.size() - 1; index >= 0; index--) {
            try {
                redisLockManager.release(redisLocks.get(index));
            } catch (RuntimeException exception) {
                if (releaseFailure == null) {
                    releaseFailure = exception;
                } else {
                    releaseFailure.addSuppressed(exception);
                }
            }
        }

        return releaseFailure;
    }

    private void validateCommand(CreateOrderCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item.");
        }
    }

    private void validateItem(CreateOrderCommand.Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Order item must not be null.");
        }
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId);
        }
    }
}
