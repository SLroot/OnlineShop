package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_order", columnList = "order_id"),
    @Index(name = "idx_payment_ref",   columnList = "transaction_ref")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {

    public enum PaymentStatus { PENDING, SUCCESS, FAILED, REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    /** شماره پیگیری شبیه‌سازی‌شده */
    @Column(name = "transaction_ref", length = 40)
    private String transactionRef;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public boolean isRefundable() {
        return status == PaymentStatus.SUCCESS;
    }
}