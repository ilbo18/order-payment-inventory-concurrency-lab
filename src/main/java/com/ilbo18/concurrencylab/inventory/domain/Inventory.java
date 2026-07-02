package com.ilbo18.concurrencylab.inventory.domain;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품별 재고 수량과 낙관적 락 version을 관리하는 재고 도메인 모델이다.
 */
@Getter
@Entity
@Table(name = "inventories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Version
    @Column(nullable = false)
    private Long version;

    public Inventory(Long productId, int quantity) {
        validateProductId(productId);
        validateInitialQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    /**
     * 주문 취소나 보상 처리에서 사용할 재고 복구 수량을 반영한다.
     */
    public void increase(int quantity) {
        validatePositiveQuantity(quantity);
        this.quantity += quantity;
    }

    /**
     * 주문 생성 시 재고를 차감하고, 요청 수량보다 재고가 부족하면 주문을 중단한다.
     */
    public void decrease(int quantity) {
        validatePositiveQuantity(quantity);
        if (this.quantity < quantity) {
            throw new CustomException(ErrorCode.INSUFFICIENT_STOCK, "Insufficient stock. quantity=" + this.quantity + ", requestedQuantity=" + quantity);
        }
        this.quantity -= quantity;
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("Product id must be positive.");
        }
    }

    private void validateInitialQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Inventory quantity must be greater than or equal to 0.");
        }
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive.");
        }
    }
}
