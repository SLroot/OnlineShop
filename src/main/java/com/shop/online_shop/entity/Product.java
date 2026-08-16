package com.shop.online_shop.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_product_category", columnList = "category_id"),
    @Index(name = "idx_product_seller",   columnList = "seller_id"),
    @Index(name = "idx_product_sku",      columnList = "sku"),
    @Index(name = "idx_product_active",   columnList = "active")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Product {

    public static final int MAX_IMAGES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String sku;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /**
     * Set به جای List است تا Hibernate بتواند این رابطه را همزمان با
     * سایر مجموعه‌ها join fetch کند؛ با چند List خطای
     * MultipleBagFetchException می‌دهد.
     */
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<ProductImage> images = new LinkedHashSet<>();

    /** soft delete — سفارش‌های قدیمی به محصول ارجاع دارند */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * محصولاتی که هنگام تعلیق فروشنده غیرفعال شدند.
     * موقع رفع تعلیق فقط همین‌ها برمی‌گردند — محصولاتی که
     * خود فروشنده غیرفعال کرده بود دست‌نخورده می‌مانند.
     */
    @Column(name = "deactivated_by_suspension", nullable = false)
    @Builder.Default
    private boolean deactivatedBySuspension = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isInStock() {
        return stock != null && stock > 0;
    }
}