package com.ilbo18.concurrencylab.payment.application.gateway;

/**
 * 실제 PG 연동 전 결제 승인 유스케이스와 외부 결제 시스템 경계를 분리하는 포트이다.
 */
public interface PaymentGateway {

    /**
     * 결제 승인 요청을 외부 결제 시스템에 위임하고 승인 성공 또는 실패 결과를 반환한다.
     */
    PaymentGatewayResult approve(PaymentGatewayApproveRequest request);
}
