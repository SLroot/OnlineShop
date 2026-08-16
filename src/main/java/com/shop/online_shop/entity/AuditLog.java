package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_actor",  columnList = "actor_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_time",   columnList = "created_at")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** کسی که عملیات را انجام داده — null برای رویدادهای ناشناس */
    @Column(name = "actor_id")
    private Long actorId;

    @Column(nullable = false, length = 64)
    private String action;

    /** جزئیات اختیاری — مثلاً "role: USER -> SELLER" */
    @Column(length = 512)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}