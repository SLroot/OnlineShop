package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddressRequest(

    @NotBlank(message = "عنوان آدرس الزامی است")
    @Size(max = 50, message = "عنوان حداکثر ۵۰ کاراکتر")
    String title,

    @NotBlank(message = "استان الزامی است")
    @Size(max = 50)
    String province,

    @NotBlank(message = "شهر الزامی است")
    @Size(max = 50)
    String city,

    @NotBlank(message = "آدرس کامل الزامی است")
    @Size(max = 500, message = "آدرس حداکثر ۵۰۰ کاراکتر")
    String fullAddress,

    @NotBlank(message = "کد پستی الزامی است")
    @Pattern(regexp = "^\\d{10}$", message = "کد پستی باید ۱۰ رقم باشد")
    String postalCode,

    boolean setAsDefault
) {}
