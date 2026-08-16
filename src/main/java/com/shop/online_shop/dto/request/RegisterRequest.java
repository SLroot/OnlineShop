package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(

    @NotBlank(message = "ایمیل الزامی است")
    @Email(message = "فرمت ایمیل نامعتبر است")
    String email,

    @NotBlank(message = "رمز عبور الزامی است")
    @Size(min = 6, message = "رمز عبور حداقل ۶ کاراکتر باشد")
    String password,

    @NotBlank(message = "نام و نام خانوادگی الزامی است")
    String fullName,

    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل نامعتبر است")
    String phone
) {}