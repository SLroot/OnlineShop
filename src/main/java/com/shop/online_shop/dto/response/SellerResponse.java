package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.SellerProfile;
import com.shop.online_shop.entity.User;

import java.time.Instant;

public record SellerResponse(
    Long userId,
    String email,
    String fullName,
    String phone,
    String shopName,
    String landline,
    String status,
    Long reviewedBy,
    Instant reviewedAt,
    String rejectionReason,
    String suspensionReason,
    Instant registeredAt
) {
    public static SellerResponse from(User user) {
        SellerProfile p = user.getSellerProfile();

        return new SellerResponse(
                user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
                p != null ? p.getShopName() : null,
                p != null ? p.getLandline() : null,
                user.getStatus().name(),
                p != null ? p.getReviewedBy() : null,
                p != null ? p.getReviewedAt() : null,
                p != null ? p.getRejectionReason() : null,
                p != null ? p.getSuspensionReason() : null,
                user.getCreatedAt());
    }
}