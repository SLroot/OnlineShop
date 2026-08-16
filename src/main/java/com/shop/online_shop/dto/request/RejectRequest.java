package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
    @NotBlank(message = "دلیل الزامی است")
    @Size(max = 500, message = "دلیل حداکثر ۵۰۰ کاراکتر")
    String reason
) {}