package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.AuditLog;
import java.time.Instant;

public record AuditLogResponse(
    Long id,
    Long actorId,
    String action,
    String details,
    String ipAddress,
    Instant createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(), log.getActorId(), log.getAction(),
                log.getDetails(), log.getIpAddress(), log.getCreatedAt());
    }
}
