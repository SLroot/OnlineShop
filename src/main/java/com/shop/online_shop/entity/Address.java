package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "addresses", indexes = {
    @Index(name = "idx_address_user", columnList = "user_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** برچسب کاربرپسند مثل «خانه» یا «محل کار» */
    @Column(nullable = false, length = 50)
    private String title;

    @Column(nullable = false, length = 50)
    private String province;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(name = "full_address", nullable = false, length = 500)
    private String fullAddress;

    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** متن یکجا برای ذخیره در سفارش */
    public String toSnapshot() {
        return String.format("%s، %s، %s — کد پستی: %s",
                province, city, fullAddress, postalCode);
    }
}