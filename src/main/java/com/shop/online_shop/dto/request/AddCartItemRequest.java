package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(

    @NotNull(message = "شناسه محصول الزامی است")
    Long productId,

    @NotNull(message = "تعداد الزامی است")
    @Min(value = 1, message = "تعداد باید حداقل ۱ باشد")
    Integer quantity
) {}