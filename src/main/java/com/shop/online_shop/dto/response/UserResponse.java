package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.User;

import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public record UserResponse(
    Long id,
    String email,
    String fullName,
    String phone,
    String role,
    String status,
    boolean mustChangePassword,
    Set<String> permissions,
    ShopInfo shop,
    Instant createdAt
) {
    public record ShopInfo(String shopName, String landline) {}

    public static UserResponse from(User user) {
        Set<String> perms = user.getRole().getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toCollection(TreeSet::new));

        ShopInfo shop = user.getSellerProfile() != null
                ? new ShopInfo(user.getSellerProfile().getShopName(),
                               user.getSellerProfile().getLandline())
                : null;

        return new UserResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getRole().getName(), user.getStatus().name(),
                user.isMustChangePassword(), perms, shop, user.getCreatedAt());
    }
}