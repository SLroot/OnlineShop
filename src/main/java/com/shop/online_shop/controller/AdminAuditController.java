package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.AuditLogResponse;
import com.shop.online_shop.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Admin — Audit", description = "مشاهده رویدادهای حساس سیستم")
public class AdminAuditController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "لیست رویدادها",
               description = "قابل فیلتر بر اساس کاربر یا نوع عملیات",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Page<AuditLogResponse>> list(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        var result = actorId != null
                ? auditLogRepository.findByActorId(actorId, pageable)
                : action != null
                    ? auditLogRepository.findByAction(action, pageable)
                    : auditLogRepository.findAll(pageable);

        return ResponseEntity.ok(result.map(AuditLogResponse::from));
    }
}
