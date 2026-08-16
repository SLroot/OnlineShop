package com.shop.online_shop.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentRequest(

    @Schema(description = "شبیه‌سازی نتیجه درگاه پرداخت. "
                        + "true یعنی پرداخت موفق، false یعنی ناموفق",
            defaultValue = "true")
    Boolean simulateSuccess
) {
    public boolean success() {
        return simulateSuccess == null || simulateSuccess;
    }
}