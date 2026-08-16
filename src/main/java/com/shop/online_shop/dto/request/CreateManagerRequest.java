package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.*;

public record CreateManagerRequest(

    @NotBlank(message = "ایمیل الزامی است")
    @Email(message = "فرمت ایمیل نامعتبر است")
    String email,

    @NotBlank(message = "رمز اولیه الزامی است")
    @Size(min = 8, message = "رمز اولیه حداقل ۸ کاراکتر باشد")
    String initialPassword,

    @NotBlank(message = "نام و نام خانوادگی الزامی است")
    String fullName,

    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل نامعتبر است")
    String phone
) {}