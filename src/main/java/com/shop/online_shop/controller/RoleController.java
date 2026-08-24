package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.RoleRequest;
import com.shop.online_shop.dto.response.PermissionGroupResponse;
import com.shop.online_shop.dto.response.PermissionResponse;
import com.shop.online_shop.dto.response.RoleResponse;
import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.RoleService;
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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles & Permissions", description = "مدیریت نقش‌ها و مجوزها")
public class RoleController {

    private final RoleService roleService;

    // ==================== خواندن ====================

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "فهرست نقش‌ها",
               description = "هر نقش به‌همراه مجوزها و تعداد کاربرانش. "
                           + "فیلد systemRole مشخص می‌کند نقش پایه است و حذف نمی‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleService.listAll().stream()
                .map(r -> RoleResponse.from(r, roleService.countUsers(r.getId())))
                .toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "جزئیات یک نقش",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<RoleResponse> getById(@PathVariable Long id) {
        var role = roleService.getById(id);
        return ResponseEntity.ok(RoleResponse.from(role, roleService.countUsers(id)));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "فهرست تخت همه مجوزهای سامانه",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<PermissionResponse>> permissions() {
        return ResponseEntity.ok(roleService.listPermissions().stream()
                .map(PermissionResponse::from)
                .toList());
    }

    @GetMapping("/permissions/grouped")
    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Operation(summary = "مجوزها گروه‌بندی‌شده بر اساس منبع",
               description = "برای ساخت درخت تیک در پنل مدیریت نقش",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<PermissionGroupResponse>> groupedPermissions() {
        Map<String, List<PermissionResponse>> grouped = new LinkedHashMap<>();

        roleService.listPermissions().stream()
                .sorted(Comparator.comparing(Permission::getResource)
                        .thenComparing(Permission::getName))
                .forEach(p -> grouped
                        .computeIfAbsent(p.getResource(), k -> new java.util.ArrayList<>())
                        .add(PermissionResponse.from(p)));

        return ResponseEntity.ok(grouped.entrySet().stream()
                .map(e -> new PermissionGroupResponse(e.getKey(), e.getValue()))
                .toList());
    }

    // ==================== نوشتن ====================

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "ساخت نقش جدید",
               description = "مجوزها با فهرست شناسه ارسال می‌شوند. "
                           + "نمی‌توانید مجوزی بدهید که خودتان ندارید",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "نقش ساخته شد"),
        @ApiResponse(responseCode = "400", description = "مجوز نامعتبر"),
        @ApiResponse(responseCode = "403", description = "مجوزی خارج از دسترسی خودتان"),
        @ApiResponse(responseCode = "409", description = "نام نقش تکراری")
    })
    public ResponseEntity<RoleResponse> create(
            @Valid @RequestBody RoleRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var role = roleService.create(request, me);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoleResponse.from(role, 0));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "ویرایش نقش",
               description = "مجموعه مجوزها با فهرست ارسالی جایگزین می‌شود. "
                           + "پس از تغییر، نشست‌های دارندگان این نقش باطل می‌گردد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ویرایش شد"),
        @ApiResponse(responseCode = "403", description = "مجوزی خارج از دسترسی خودتان")
    })
    public ResponseEntity<RoleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var role = roleService.update(id, request, me);
        return ResponseEntity.ok(RoleResponse.from(role, roleService.countUsers(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "حذف نقش",
               description = "نقش پایه حذف نمی‌شود و نقشی که کاربر دارد نیز",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "حذف شد"),
        @ApiResponse(responseCode = "403", description = "نقش پایه است"),
        @ApiResponse(responseCode = "409", description = "کاربرانی این نقش را دارند")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        roleService.delete(id, me);
        return ResponseEntity.noContent().build();
    }

    // ==================== مجوز تکی ====================

    @PostMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "افزودن یک مجوز به نقش",
               description = "معادل زدن یک تیک در پنل",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<RoleResponse> grant(
            @PathVariable Long roleId,
            @PathVariable Long permissionId,
            @AuthenticationPrincipal UserPrincipal me) {

        var role = roleService.grantPermission(roleId, permissionId, me);
        return ResponseEntity.ok(RoleResponse.brief(role));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    @Operation(summary = "حذف یک مجوز از نقش",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<RoleResponse> revoke(
            @PathVariable Long roleId,
            @PathVariable Long permissionId,
            @AuthenticationPrincipal UserPrincipal me) {

        var role = roleService.revokePermission(roleId, permissionId, me);
        return ResponseEntity.ok(RoleResponse.brief(role));
    }
}