package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Address;

import java.time.Instant;

public record AddressResponse(
    Long id,
    String title,
    String province,
    String city,
    String fullAddress,
    String postalCode,
    boolean isDefault,
    Instant createdAt,
    OwnerInfo owner
) {
    public record OwnerInfo(Long userId, String fullName, String email) {}

    /** برای کاربر — بدون اطلاعات مالک چون خودش است */
    public static AddressResponse from(Address a) {
        return new AddressResponse(
                a.getId(), a.getTitle(), a.getProvince(), a.getCity(),
                a.getFullAddress(), a.getPostalCode(), a.isDefault(),
                a.getCreatedAt(), null);
    }

    /** برای مدیر — با اطلاعات مالک */
    public static AddressResponse withOwner(Address a) {
        return new AddressResponse(
                a.getId(), a.getTitle(), a.getProvince(), a.getCity(),
                a.getFullAddress(), a.getPostalCode(), a.isDefault(),
                a.getCreatedAt(),
                new OwnerInfo(a.getUser().getId(),
                              a.getUser().getFullName(),
                              a.getUser().getEmail()));
    }
}
