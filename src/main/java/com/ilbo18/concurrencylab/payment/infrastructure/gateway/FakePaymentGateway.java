package com.ilbo18.concurrencylab.payment.infrastructure.gateway;

import com.ilbo18.concurrencylab.payment.application.gateway.PaymentGateway;
import com.ilbo18.concurrencylab.payment.application.gateway.PaymentGatewayApproveRequest;
import com.ilbo18.concurrencylab.payment.application.gateway.PaymentGatewayResult;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 실제 외부 PG를 붙이기 전 승인 흐름을 검증하기 위한 기본 Fake 결제 게이트웨이다.
 */
@Component
public class FakePaymentGateway implements PaymentGateway {

    /**
     * 운영 코드에는 실패 조건을 숨기지 않고, 실패 케이스는 테스트에서 PaymentGateway Bean을 대체해 검증한다.
     */
    @Override
    public PaymentGatewayResult approve(PaymentGatewayApproveRequest request) {
        return PaymentGatewayResult.success(UUID.randomUUID().toString());
    }
}
