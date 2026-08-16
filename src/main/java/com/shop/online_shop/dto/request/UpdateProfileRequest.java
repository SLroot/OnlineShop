package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(

    @NotBlank(message = "نام و نام خانوادگی الزامی است")
    String fullName,

    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل نامعتبر است")
    String phone
) {}
