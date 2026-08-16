package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cart_items",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_cart_product",
           columnNames = {"cart_id", "product_id"}),
       indexes = {
           @Index(name = "idx_cart_item_cart",    columnList = "cart_id"),
           @Index(name = "idx_cart_item_product", columnList = "product_id")
       })
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @PrePersist
    void onCreate() {
        addedAt = Instant.now();
    }
}