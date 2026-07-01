package com.ilbo18.concurrencylab.order.presentation;

import com.ilbo18.concurrencylab.order.application.OrderService;
import com.ilbo18.concurrencylab.order.domain.OrderEntity;
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
 * 상품과 재고를 기반으로 주문을 생성하고 주문 단건 정보를 조회하는 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문 항목 기준으로 재고를 차감하고 주문을 생성한다.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderEntity order = orderService.create(request.toCommand());

        return ResponseEntity
                .created(URI.create("/api/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    /**
     * 생성된 주문을 주문 항목과 함께 조회한다.
     */
    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable Long orderId) {
        return OrderResponse.from(orderService.get(orderId));
    }
}
