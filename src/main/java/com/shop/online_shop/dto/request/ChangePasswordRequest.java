package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(

    @NotBlank(message = "رمز فعلی الزامی است")
    String currentPassword,

    @NotBlank(message = "رمز جدید الزامی است")
    @Size(min = 6, message = "رمز جدید حداقل ۶ کاراکتر باشد")
    String newPassword
) {}
