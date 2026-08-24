package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChangeRoleRequest(

    @NotNull(message = "انتخاب نقش الزامی است")
    Long roleId
) {}