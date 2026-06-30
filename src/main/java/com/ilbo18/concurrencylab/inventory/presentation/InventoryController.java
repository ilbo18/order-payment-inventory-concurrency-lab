package com.ilbo18.concurrencylab.inventory.presentation;

import com.ilbo18.concurrencylab.inventory.application.InventoryService;
import com.ilbo18.concurrencylab.inventory.domain.Inventory;
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
 * 주문 동시성 테스트에 필요한 상품별 재고 사전 데이터를 등록하고 조회하는 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    /**
     * 상품 ID를 기준으로 주문 테스트에 사용할 초기 재고를 등록한다.
     */
    @PostMapping
    public ResponseEntity<InventoryResponse> register(@Valid @RequestBody CreateInventoryRequest request) {
        Inventory inventory = inventoryService.register(request.productId(), request.quantity());

        return ResponseEntity
                .created(URI.create("/api/inventories/products/" + inventory.getProductId()))
                .body(InventoryResponse.from(inventory));
    }

    /**
     * 상품 ID를 기준으로 현재 등록된 재고 정보를 조회한다.
     */
    @GetMapping("/products/{productId}")
    public InventoryResponse getByProductId(@PathVariable Long productId) {
        return InventoryResponse.from(inventoryService.getByProductId(productId));
    }
}
