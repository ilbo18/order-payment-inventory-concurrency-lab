package com.ilbo18.concurrencylab.payment.domain;

import com.ilbo18.concurrencylab.common.exception.CustomException;
import com.ilbo18.concurrencylab.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문에 대한 내부 결제 승인 상태와 결제 금액을 관리하는 결제 도메인 모델이다.
 */
@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 100)
    private String idempotencyKey;

    @Column(length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 255)
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private Payment(Long orderId, BigDecimal amount, String idempotencyKey, String requestHash) {
        validateOrderId(orderId);
        validateAmount(amount);
        validateIdempotencyKey(idempotencyKey);
        validateRequestHash(requestHash);
        this.orderId = orderId;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.status = PaymentStatus.READY;
    }

    /**
     * 주문 금액 검증이 끝난 결제 요청을 승인 대기 상태로 생성한다.
     */
    public static Payment ready(Long orderId, BigDecimal amount, String idempotencyKey, String requestHash) {
        return new Payment(orderId, amount, idempotencyKey, requestHash);
    }

    /**
     * 결제 승인 성공 시 결제 상태를 APPROVED로 확정한다.
     */
    public void approve() {
        validateNotCompleted();
        this.status = PaymentStatus.APPROVED;
        this.failureReason = null;
    }

    /**
     * 결제 승인 실패 시 실패 사유와 함께 결제 상태를 FAILED로 확정한다.
     */
    public void fail(String reason) {
        validateNotCompleted();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Payment failure reason must not be blank.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    /**
     * 승인된 결제를 내부 취소 상태로 전환한다. 외부 PG 환불 연동은 아직 수행하지 않는다.
     */
    public void cancel() {
        if (this.status != PaymentStatus.APPROVED) {
            throw new CustomException(ErrorCode.PAYMENT_CANNOT_CANCEL, "Payment cannot cancel. paymentId=" + this.id + ", status=" + this.status);
        }

        this.status = PaymentStatus.CANCELED;
        // 취소는 결제 승인 실패가 아니므로 failureReason은 기록하지 않는다.
        this.failureReason = null;
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private void validateNotCompleted() {
        if (this.status == PaymentStatus.APPROVED || this.status == PaymentStatus.FAILED || this.status == PaymentStatus.CANCELED) {
            throw new CustomException(ErrorCode.PAYMENT_ALREADY_COMPLETED, "Payment already completed. paymentId=" + this.id + ", status=" + this.status);
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Payment order id must be positive.");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Payment amount must be greater than or equal to 0.");
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key must not be blank.");
        }
        if (idempotencyKey.length() > 100) {
            throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Idempotency-Key must be 100 characters or fewer.");
        }
    }

    private void validateRequestHash(String requestHash) {
        if (requestHash == null || requestHash.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY, "Payment request hash must not be blank.");
        }
    }
}
