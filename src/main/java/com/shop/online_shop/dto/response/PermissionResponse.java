package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Permission;

public record PermissionResponse(
    Long id,
    String name,
    String resource,
    String action,
    String description
) {
    public static PermissionResponse from(Permission p) {
        return new PermissionResponse(p.getId(), p.getName(), p.getResource(),
                                      p.getAction(), p.getDescription());
    }
}