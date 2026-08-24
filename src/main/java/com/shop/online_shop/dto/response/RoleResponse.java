package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record RoleResponse(
    Long id,
    String code,
    String name,
    String description,
    boolean systemRole,
    boolean custom,
    boolean requiresSellerApproval,
    boolean openRegistration,
    long userCount,
    List<PermissionResponse> permissions,
    Instant createdAt
) {
    public static RoleResponse from(Role role, long userCount) {
        List<PermissionResponse> perms = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getName))
                .map(PermissionResponse::from)
                .toList();

        return new RoleResponse(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.isSystemRole(),
                role.isCustom(),
                role.isRequiresSellerApproval(),
                role.isOpenRegistration(),
                userCount,
                perms,
                role.getCreatedAt());
    }

    /** بدون شمارش کاربران — برای جاهایی که این عدد لازم نیست */
    public static RoleResponse brief(Role role) {
        return from(role, -1);
    }
}