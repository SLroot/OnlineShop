package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotNull;

public record PlaceOrderRequest(

    @NotNull(message = "انتخاب آدرس الزامی است")
    Long addressId
) {}