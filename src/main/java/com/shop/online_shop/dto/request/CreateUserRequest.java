package com.shop.online_shop.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(

    @NotBlank(message = "ایمیل الزامی است")
    @Email(message = "فرمت ایمیل نامعتبر است")
    String email,

    @NotBlank(message = "رمز اولیه الزامی است")
    @Size(min = 8, message = "رمز اولیه حداقل ۸ کاراکتر باشد")
    String initialPassword,

    @NotBlank(message = "نام و نام خانوادگی الزامی است")
    String fullName,

    @Pattern(regexp = "^09\\d{9}$", message = "شماره موبایل نامعتبر است")
    String phone,

    /** هر نقشی، پایه یا سفارشی — به شرط اینکه فراتر از دسترسی سازنده نباشد */
    @NotNull(message = "انتخاب نقش الزامی است")
    Long roleId
) {}