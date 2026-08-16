package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(

    @NotBlank(message = "کد کالا الزامی است")
    @Size(max = 40, message = "کد کالا حداکثر ۴۰ کاراکتر")
    String sku,

    @NotBlank(message = "نام محصول الزامی است")
    @Size(max = 200, message = "نام حداکثر ۲۰۰ کاراکتر")
    String name,

    @Size(max = 2000, message = "توضیحات حداکثر ۲۰۰۰ کاراکتر")
    String description,

    @NotNull(message = "قیمت الزامی است")
    @DecimalMin(value = "0.0", inclusive = false, message = "قیمت باید بزرگ‌تر از صفر باشد")
    BigDecimal price,

    @NotNull(message = "موجودی الزامی است")
    @Min(value = 0, message = "موجودی نمی‌تواند منفی باشد")
    Integer stock,

    @NotNull(message = "دسته‌بندی الزامی است")
    Long categoryId,

    /**
     * فقط برای مدیر و ادمین: ثبت محصول به نام یک فروشنده مشخص.
     * اگر فروشنده عادی این فیلد را بفرستد نادیده گرفته می‌شود
     * و محصول به نام خودش ثبت می‌گردد.
     */
    Long sellerId
) {}