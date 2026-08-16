package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(

    @NotBlank(message = "ایمیل الزامی است")
    @Email(message = "فرمت ایمیل نامعتبر است")
    String email
) {}