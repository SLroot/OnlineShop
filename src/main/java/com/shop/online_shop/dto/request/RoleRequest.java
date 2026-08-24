package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record RoleRequest(

    @NotBlank(message = "نام نقش الزامی است")
    @Size(max = 64, message = "نام نقش حداکثر ۶۴ کاراکتر")
    String name,

    @Size(max = 200, message = "توضیحات حداکثر ۲۰۰ کاراکتر")
    String description,

    /** شناسه مجوزها — همان تیک‌هایی که در پنل زده می‌شود */
    Set<Long> permissionIds,

    /** دارندگان این نقش باید تأیید شوند و پروفایل فروشگاه بگیرند */
    boolean requiresSellerApproval
) {}