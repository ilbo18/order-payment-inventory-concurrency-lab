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
 * Inventory version 충돌을 이용해 같은 상품 재고의 동시 수정을 감지하는 낙관적 락 주문 유스케이스다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OptimisticOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * 재고 행을 잠그지 않고 주문을 생성하며, 커밋 시점에 version이 바뀌면 낙관적 락 예외로 충돌을 알린다.
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

    private void validateCommand(CreateOrderCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item.");
        }
    }

    private OrderItem createOrderItem(CreateOrderCommand.Item item) {
        validateItem(item);
        Product product = getProduct(item.productId());
        Inventory inventory = getInventory(item.productId());

        // 낙관적 락은 같은 Inventory가 동시에 수정되면 flush/commit 시점에 version 충돌 예외가 발생할 수 있다.
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
        return inventoryRepository.findByProductIdForOptimistic(productId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found. productId=" + productId));
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId);
        }
    }
}
