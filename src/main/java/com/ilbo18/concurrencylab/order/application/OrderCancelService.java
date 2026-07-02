package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderItem;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.payment.infrastructure.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 주문 취소, 재고 복구, 내부 결제 취소를 하나의 트랜잭션으로 처리하는 유스케이스이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCancelService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final PaymentRepository paymentRepository;

    /**
     * 주문 항목별로 차감했던 재고를 복구하고, 승인된 결제가 있으면 내부 결제 상태도 취소한다.
     */
    @Transactional
    public OrderEntity cancel(Long orderId) {
        validateOrderId(orderId);
        OrderEntity order = getOrder(orderId);

        order.cancel();
        restoreInventories(order);
        cancelPaymentIfExists(order);

        return order;
    }

    private OrderEntity getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORDER_NOT_FOUND, "Order not found. orderId=" + orderId));
    }

    private void restoreInventories(OrderEntity order) {
        Map<Long, Integer> quantitiesByProductId = order.getOrderItems()
                .stream()
                .collect(Collectors.groupingBy(
                        OrderItem::getProductId,
                        TreeMap::new,
                        Collectors.summingInt(OrderItem::getQuantity)
                ));

        quantitiesByProductId.forEach((productId, quantity) -> {
            // 주문 생성과 취소가 동시에 같은 재고 row를 수정할 수 있으므로 같은 비관적 락 기준으로 직렬화한다.
            Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId)
                    .orElseThrow(() -> new CustomException(ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found. productId=" + productId));
            inventory.increase(quantity);
        });
    }

    private void cancelPaymentIfExists(OrderEntity order) {
        paymentRepository.findByOrderId(order.getId())
                // 외부 PG 환불은 아직 구현하지 않았으므로 내부 Payment 상태만 CANCELED로 변경한다.
                .ifPresent(payment -> payment.cancel());
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new CustomException(ErrorCode.ORDER_NOT_FOUND, "Order not found. orderId=" + orderId);
        }
    }
}
