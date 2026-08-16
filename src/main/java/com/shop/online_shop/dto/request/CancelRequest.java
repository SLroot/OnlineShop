package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.Size;

public record CancelRequest(
    @Size(max = 500, message = "دلیل حداکثر ۵۰۰ کاراکتر")
    String reason
) {}