package com.shop.online_shop.repository;

import com.shop.online_shop.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByActorId(Long actorId, Pageable pageable);
    Page<AuditLog> findByAction(String action, Pageable pageable);
}