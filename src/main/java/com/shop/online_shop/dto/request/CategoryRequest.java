package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

    @NotBlank(message = "نام دسته‌بندی الزامی است")
    @Size(max = 100, message = "نام حداکثر ۱۰۰ کاراکتر")
    String name,

    @Size(max = 500, message = "توضیحات حداکثر ۵۰۰ کاراکتر")
    String description,

    /** null برای دسته‌بندی ریشه */
    Long parentId
) {}