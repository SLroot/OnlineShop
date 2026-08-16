package com.shop.online_shop.service;

import com.shop.online_shop.entity.AuditLog;
import com.shop.online_shop.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // REQUIRES_NEW so the audit record survives even if the main transaction rolls back
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String action, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .actorId(actorId)
                    .action(action)
                    .details(details)
                    .ipAddress(currentIp())
                    .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // audit logging must never break the main operation
            log.error("Failed to write audit log: {} / {}", action, e.getMessage());
        }
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}