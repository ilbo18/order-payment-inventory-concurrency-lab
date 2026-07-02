package com.ilbo18.concurrencylab.inventory.application;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
import com.ilbo18.concurrencylab.inventory.infrastructure.InventoryRepository;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상품별 재고 등록과 조회 유스케이스의 트랜잭션 경계를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;

    /**
     * 주문 테스트에 사용할 상품 재고를 등록하고, 상품별 재고가 중복 생성되지 않도록 차단한다.
     */
    @Transactional
    public Inventory register(Long productId, int quantity) {
        validateQuantity(quantity);
        validateProductExists(productId);
        validateInventoryNotExists(productId);

        return inventoryRepository.save(new Inventory(productId, quantity));
    }

    /**
     * 상품 ID 기준으로 재고를 조회하고, 주문 준비에 필요한 상품 또는 재고가 없으면 예외로 중단한다.
     */
    public Inventory getByProductId(Long productId) {
        validateProductExists(productId);
        return inventoryRepository.findByProductId(productId).orElseThrow(() -> new CustomException(ErrorCode.INVENTORY_NOT_FOUND, "Inventory not found. productId=" + productId));
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Inventory quantity must be greater than or equal to 0.");
        }
    }

    private void validateProductExists(Long productId) {
        if (productId == null || productId <= 0 || !productRepository.existsById(productId)) {
            throw new CustomException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId);
        }
    }

    private void validateInventoryNotExists(Long productId) {
        // 상품별 재고 기준이 하나로 유지되어야 이후 재고 차감 동시성 테스트가 같은 행을 경합한다.
        if (inventoryRepository.findByProductId(productId).isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATE_INVENTORY, "Inventory already exists. productId=" + productId);
        }
    }
}
