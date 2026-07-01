package com.ilbo18.concurrencylab.order.application;

import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.common.exception.NotFoundException;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
import com.ilbo18.concurrencylab.order.domain.OrderItem;
import com.ilbo18.concurrencylab.order.infrastructure.OrderRepository;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품과 재고를 기준으로 주문을 생성하고 주문 조회 트랜잭션을 관리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * 주문 항목의 상품과 재고를 확인한 뒤 재고를 차감하고 주문을 생성한다.
     */
    @Transactional
    public OrderEntity create(CreateOrderCommand command) {
        validateCommand(command);
        List<OrderItem> orderItems = command.items()
                .stream()
                .map(this::createOrderItem)
                .toList();

        return orderRepository.save(new OrderEntity(orderItems));
    }

    /**
     * 주문 단건을 주문 항목과 함께 조회하고, 주문이 없으면 예외로 중단한다.
     */
    public OrderEntity get(Long orderId) {
        validateOrderId(orderId);
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND, "Order not found. orderId=" + orderId));
    }

    private void validateCommand(CreateOrderCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item.");
        }
    }

    private OrderItem createOrderItem(CreateOrderCommand.Item item) {
        validateItem(item);
        Product product = getProduct(item.productId());
        Inventory inventory = getInventory(item.productId());

        // 이번 단계는 락 없이 현재 조회된 재고를 바로 차감해 다음 단계에서 동시성 문제를 재현할 수 있게 둔다.
        inventory.decrease(item.quantity());

        return new OrderItem(product.getId(), item.quantity(), product.getPrice());
    }

    private void validateItem(CreateOrderCommand.Item item) {
        if (item == null) {
            throw new IllegalArgumentException("Order item must not be null.");
        }
    }

    private Product getProduct(Long productId) {
        validateProductId(productId);
        return productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId));
    }

    private Inventory getInventory(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found. productId=" + productId));
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId);
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND, "Order not found. orderId=" + orderId);
        }
    }
}
