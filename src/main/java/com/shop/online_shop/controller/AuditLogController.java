package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.AuditLogResponse;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "Audit Log", description = "گزارش رویدادهای حساس سامانه")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_READ')")
    @Operation(summary = "فهرست رویدادها",
               description = "قابل فیلتر بر اساس کاربر انجام‌دهنده یا نوع عملیات",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<AuditLogResponse>> list(
            @Parameter(description = "شناسه کاربر انجام‌دهنده")
            @RequestParam(required = false) Long actorId,

            @Parameter(description = "نام عملیات — مانند LOGIN_SUCCESS")
            @RequestParam(required = false) String action,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        var result = actorId != null
                ? auditLogRepository.findByActorId(actorId, pageable)
                : action != null
                    ? auditLogRepository.findByAction(action, pageable)
                    : auditLogRepository.findAll(pageable);

        return ResponseEntity.ok(PagedResponse.from(result, AuditLogResponse::from));
    }
}