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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles", indexes = {
    @Index(name = "idx_role_code", columnList = "code")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * شناسه ماشینی و تغییرناپذیر — تنها برای نقش‌های پایه.
     * نقش‌های سفارشی این فیلد را ندارند و کد برنامه هرگز به نامشان ارجاع نمی‌دهد.
     */
    @Column(unique = true, length = 32)
    private String code;

    /** عنوان نمایشی — همیشه قابل ویرایش، حتی برای نقش‌های پایه */
    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 200)
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    /** نقش پایه سامانه — قابل حذف نیست، ولی نام و مجوزهایش قابل تغییر است */
    @Column(name = "system_role", nullable = false)
    @Builder.Default
    private boolean systemRole = false;

    /**
     * دارندگان این نقش پروفایل فروشگاه دارند و حسابشان تا تأیید مدیر
     * در وضعیت انتظار می‌ماند. جایگزین وابستگی کد به نام نقش SELLER.
     */
    @Column(name = "requires_seller_approval", nullable = false)
    @Builder.Default
    private boolean requiresSellerApproval = false;

    /** آیا کاربر می‌تواند در ثبت‌نام آزاد این نقش را بگیرد */
    @Column(name = "open_registration", nullable = false)
    @Builder.Default
    private boolean openRegistration = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** نقش سفارشی — توسط مدیر سامانه ساخته شده، نه seeder */
    public boolean isCustom() {
        return code == null;
    }

    public boolean hasPermission(String permissionName) {
        return permissions.stream().anyMatch(p -> p.getName().equals(permissionName));
    }
}