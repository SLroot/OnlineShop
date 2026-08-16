package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_user",   columnList = "user_id"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created", columnList = "created_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Order {

    /** مهلت پرداخت به دقیقه */
    public static final int PAYMENT_WINDOW_MINUTES = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    /**
     * آدرس به صورت متن ذخیره می‌شود نه کلید خارجی،
     * تا اگر کاربر بعداً آدرسش را عوض کرد سفارش قدیمی درست بماند.
     */
    @Column(name = "shipping_address", nullable = false, length = 600)
    private String shippingAddress;

    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL)
    private Payment payment;

    /** پس از این زمان اگر پرداخت نشود، خودکار لغو می‌گردد */
    @Column(name = "payment_deadline")
    private Instant paymentDeadline;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isPaymentExpired() {
        return status == OrderStatus.PENDING_PAYMENT
                && paymentDeadline != null
                && paymentDeadline.isBefore(Instant.now());
    }
}