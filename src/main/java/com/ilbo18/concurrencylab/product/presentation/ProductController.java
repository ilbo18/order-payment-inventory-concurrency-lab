package com.ilbo18.concurrencylab.product.presentation;

import com.ilbo18.concurrencylab.product.application.ProductService;
import com.ilbo18.concurrencylab.product.domain.Product;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 주문 동시성 테스트에 필요한 상품 사전 데이터를 등록하고 조회하는 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    /**
     * 주문 테스트 대상이 될 상품을 등록한다.
     */
    @PostMapping
    public ResponseEntity<ProductResponse> register(@Valid @RequestBody CreateProductRequest request) {
        Product product = productService.register(request.name(), request.price());

        return ResponseEntity
                .created(URI.create("/api/products/" + product.getId()))
                .body(ProductResponse.from(product));
    }

    /**
     * 주문 준비 화면이나 테스트에서 사용할 상품 단건 정보를 조회한다.
     */
    @GetMapping("/{productId}")
    public ProductResponse get(@PathVariable Long productId) {
        return ProductResponse.from(productService.get(productId));
    }
}
