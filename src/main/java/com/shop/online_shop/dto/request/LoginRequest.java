package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "ایمیل الزامی است") String email,
    @NotBlank(message = "رمز عبور الزامی است") String password
) {}