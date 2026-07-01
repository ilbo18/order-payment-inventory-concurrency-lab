package com.ilbo18.concurrencylab.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주문 생성 시점의 상태와 주문 항목 합계 금액을 관리하는 주문 도메인 모델이다.
 */
@Entity
@Table(name = "customer_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Getter
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    /**
     * 주문을 생성하면서 최초 상태를 CREATED로 두고 주문 총액을 항목 금액 합계로 확정한다.
     */
    public OrderEntity(List<OrderItem> orderItems) {
        validateOrderItems(orderItems);
        this.status = OrderStatus.CREATED;
        orderItems.forEach(this::addOrderItem);
        this.totalAmount = calculateTotalAmount();
    }

    /**
     * 결제 승인 이후 주문을 확정 상태로 전환한다.
     */
    public void confirm() {
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * 결제 실패나 사용자 취소 시 주문을 취소 상태로 전환한다.
     */
    public void cancel() {
        this.status = OrderStatus.CANCELED;
    }

    public List<OrderItem> getOrderItems() {
        return Collections.unmodifiableList(orderItems);
    }

    private void validateOrderItems(List<OrderItem> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item.");
        }
    }

    private void addOrderItem(OrderItem orderItem) {
        if (orderItem == null) {
            throw new IllegalArgumentException("Order item must not be null.");
        }
        orderItem.assignOrder(this);
        this.orderItems.add(orderItem);
    }

    private BigDecimal calculateTotalAmount() {
        return orderItems.stream()
                .map(OrderItem::getLineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
