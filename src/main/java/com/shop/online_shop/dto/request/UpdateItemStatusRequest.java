package com.shop.online_shop.dto.request;

import com.shop.online_shop.entity.OrderItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateItemStatusRequest(

    @NotNull(message = "وضعیت جدید الزامی است")
    OrderItemStatus status
) {}