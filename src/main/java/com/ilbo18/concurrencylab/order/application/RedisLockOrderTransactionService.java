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
 * Redis lock을 획득한 뒤 실제 주문 생성과 재고 차감을 하나의 DB 트랜잭션으로 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RedisLockOrderTransactionService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;

    /**
     * Redis lock이 같은 상품 요청을 막고 있으므로 DB row lock 없이 일반 재고 조회 후 차감한다.
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
}
