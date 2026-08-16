package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank(message = "refresh token الزامی است") String refreshToken
) {}
