package com.ilbo18.concurrencylab.payment.presentation;

import com.ilbo18.concurrencylab.payment.application.PaymentService;
import com.ilbo18.concurrencylab.payment.domain.Payment;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * 주문 금액 검증과 PaymentGateway 결과 기반의 결제 승인/실패 조회 API를 제공한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Idempotency-Key 기준으로 중복 요청을 식별하고, 신규 요청은 PaymentGateway 승인 결과에 따라 결제 상태를 확정한다.
     */
    @PostMapping("/approve")
    public ResponseEntity<PaymentResponse> approve(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentApproveRequest request
    ) {
        Payment payment = paymentService.approve(request.toCommand(idempotencyKey));

        return ResponseEntity
                .created(URI.create("/api/payments/" + payment.getId()))
                .body(PaymentResponse.from(payment));
    }

    /**
     * 결제 ID 기준으로 결제 승인 결과를 조회한다.
     */
    @GetMapping("/{paymentId}")
    public PaymentResponse get(@PathVariable Long paymentId) {
        return PaymentResponse.from(paymentService.get(paymentId));
    }

    /**
     * 주문 ID 기준으로 결제 승인 결과를 조회한다.
     */
    @GetMapping("/orders/{orderId}")
    public PaymentResponse getByOrderId(@PathVariable Long orderId) {
        return PaymentResponse.from(paymentService.getByOrderId(orderId));
    }
}
