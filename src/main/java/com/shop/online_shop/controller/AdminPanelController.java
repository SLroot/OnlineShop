package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.CreateManagerRequest;
import com.shop.online_shop.dto.response.RoleResponse;
import com.shop.online_shop.dto.response.UserResponse;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Panel", description = "پنل ادمین: مدیران و مجوزهای نقش‌ها")
public class AdminPanelController {

    private final AdminService adminService;

    // ==================== مدیران ====================

    @PostMapping("/managers")
    @Operation(summary = "ساخت حساب مدیر",
               description = "رمز اولیه را به مدیر تحویل دهید. "
                           + "در اولین ورود موظف به تغییر آن است",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "مدیر ساخته شد"),
        @ApiResponse(responseCode = "409", description = "ایمیل تکراری")
    })
    public ResponseEntity<UserResponse> createManager(
            @Valid @RequestBody CreateManagerRequest request,
            @AuthenticationPrincipal UserPrincipal me) {
        var manager = adminService.createManager(request, me.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(manager));
    }

    @PatchMapping("/managers/{id}/suspend")
    @Operation(summary = "تعلیق مدیر",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserResponse> suspendManager(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {
        return ResponseEntity.ok(
                UserResponse.from(adminService.suspendManager(id, me.getId())));
    }

    @PatchMapping("/managers/{id}/activate")
    @Operation(summary = "فعال‌سازی مجدد مدیر",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserResponse> activateManager(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {
        return ResponseEntity.ok(
                UserResponse.from(adminService.activateManager(id, me.getId())));
    }

    // ==================== نقش‌ها و مجوزها ====================

    @GetMapping("/roles")
    @Operation(summary = "لیست نقش‌ها با مجوزهایشان",
               description = "فیلد editable مشخص می‌کند مجوزهای آن نقش قابل تغییر است یا نه",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<RoleResponse>> roles() {
        return ResponseEntity.ok(adminService.listRoles().stream()
                .map(r -> RoleResponse.from(r, adminService.isEditable(r)))
                .toList());
    }

    @GetMapping("/permissions")
    @Operation(summary = "لیست همه مجوزهای سیستم",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<RoleResponse.PermissionResponse>> permissions() {
        return ResponseEntity.ok(adminService.listPermissions().stream()
                .map(p -> new RoleResponse.PermissionResponse(
                        p.getId(), p.getName(), p.getResource(),
                        p.getAction(), p.getDescription()))
                .toList());
    }

    @PostMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "افزودن مجوز به نقش",
               description = "فقط برای MANAGER و SELLER. "
                           + "نشست‌های همه کاربران آن نقش باطل می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "مجوز اضافه شد"),
        @ApiResponse(responseCode = "403", description = "این نقش قابل ویرایش نیست")
    })
    public ResponseEntity<RoleResponse> grant(
            @PathVariable Long roleId,
            @PathVariable Long permissionId,
            @AuthenticationPrincipal UserPrincipal me) {
        var role = adminService.grantPermission(roleId, permissionId, me.getId());
        return ResponseEntity.ok(RoleResponse.from(role, true));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @Operation(summary = "حذف مجوز از نقش",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<RoleResponse> revoke(
            @PathVariable Long roleId,
            @PathVariable Long permissionId,
            @AuthenticationPrincipal UserPrincipal me) {
        var role = adminService.revokePermission(roleId, permissionId, me.getId());
        return ResponseEntity.ok(RoleResponse.from(role, true));
    }
}