package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "order_items", indexes = {
    @Index(name = "idx_order_item_order",  columnList = "order_id"),
    @Index(name = "idx_order_item_seller", columnList = "seller_id"),
    @Index(name = "idx_order_item_status", columnList = "status")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * فروشنده جداگانه ذخیره می‌شود تا کوئری اقلام یک فروشنده
     * نیازی به join با محصول نداشته باشد.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** نام محصول در لحظه خرید — اگر بعداً عوض شد، سفارش دست‌نخورده می‌ماند */
    @Column(name = "product_name", nullable = false, length = 200)
    private String productName;

    @Column(name = "product_sku", nullable = false, length = 40)
    private String productSku;

    /** قیمت واحد در لحظه خرید */
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal lineTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private OrderItemStatus status = OrderItemStatus.PENDING_PAYMENT;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "status_updated_at")
    private Instant statusUpdatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        statusUpdatedAt = Instant.now();
    }
}