package com.ilbo18.concurrencylab.product.application;

import com.ilbo18.concurrencylab.common.exception.NotFoundException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import com.ilbo18.concurrencylab.product.domain.Product;
import com.ilbo18.concurrencylab.product.infrastructure.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 상품 등록과 조회 유스케이스의 트랜잭션 경계를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * 주문 테스트에 사용할 상품을 등록하고 저장된 상품을 반환한다.
     */
    @Transactional
    public Product register(String name, BigDecimal price) {
        return productRepository.save(new Product(name, price));
    }

    /**
     * 상품 단건 조회를 수행하고, 주문 준비에 사용할 상품이 없으면 예외로 중단한다.
     */
    public Product get(Long productId) {
        validateProductId(productId);
        return productRepository.findById(productId).orElseThrow(() -> new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId));
    }

    private void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "Product not found. productId=" + productId);
        }
    }
}
