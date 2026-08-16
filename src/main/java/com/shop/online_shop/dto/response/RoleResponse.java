package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;

import java.util.List;

public record RoleResponse(
    Long id,
    String name,
    String description,
    boolean editable,
    List<PermissionResponse> permissions
) {
    public record PermissionResponse(Long id, String name, String resource,
                                     String action, String description) {
        static PermissionResponse from(Permission p) {
            return new PermissionResponse(p.getId(), p.getName(), p.getResource(),
                                          p.getAction(), p.getDescription());
        }
    }

    public static RoleResponse from(Role role, boolean editable) {
        return new RoleResponse(
                role.getId(), role.getName(), role.getDescription(), editable,
                role.getPermissions().stream()
                        .map(PermissionResponse::from)
                        .sorted((a, b) -> a.name().compareTo(b.name()))
                        .toList());
    }
}