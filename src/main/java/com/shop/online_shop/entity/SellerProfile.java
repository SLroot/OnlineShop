package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "seller_profiles")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SellerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "shop_name", nullable = false, length = 128)
    private String shopName;

    @Column(name = "landline", nullable = false, length = 20)
    private String landline;

    /** توسط چه کسی و چه زمانی بررسی شد */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "suspension_reason", length = 500)
    private String suspensionReason;
}