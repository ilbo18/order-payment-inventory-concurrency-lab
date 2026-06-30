package com.ilbo18.concurrencylab.inventory.domain;

import com.ilbo18.concurrencylab.common.exception.InsufficientStockException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public Inventory(Long productId, int quantity) {
        validateProductId(productId);
        validateInitialQuantity(quantity);
        this.productId = productId;
        this.quantity = quantity;
    }

    public void increase(int quantity) {
        validatePositiveQuantity(quantity);
        this.quantity += quantity;
    }

    public void decrease(int quantity) {
        validatePositiveQuantity(quantity);
        if (this.quantity < quantity) {
            throw new InsufficientStockException(this.quantity, quantity);
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
