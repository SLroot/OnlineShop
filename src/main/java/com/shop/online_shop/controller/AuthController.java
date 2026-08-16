package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.ChangePasswordRequest;
import com.shop.online_shop.dto.request.ForgotPasswordRequest;
import com.shop.online_shop.dto.request.LoginRequest;
import com.shop.online_shop.dto.request.RefreshRequest;
import com.shop.online_shop.dto.request.RegisterRequest;
import com.shop.online_shop.dto.request.ResetPasswordRequest;
import com.shop.online_shop.dto.request.SellerRegisterRequest;
import com.shop.online_shop.dto.request.UpdateProfileRequest;
import com.shop.online_shop.dto.response.AuthResponse;
import com.shop.online_shop.dto.response.UserResponse;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.AuthService;
import com.shop.online_shop.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "ثبت‌نام، ورود، نشست و پروفایل کاربر")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    // ==================== ثبت‌نام ====================

    @PostMapping("/register")
    @Operation(summary = "ثبت‌نام مشتری",
               description = "حساب بلافاصله فعال می‌شود و توکن دریافت می‌کنید")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "ثبت‌نام موفق"),
        @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی"),
        @ApiResponse(responseCode = "409", description = "ایمیل تکراری")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerCustomer(request));
    }

    @PostMapping("/register/seller")
    @Operation(summary = "ثبت‌نام فروشنده",
               description = "حساب در وضعیت «در انتظار تأیید» ثبت می‌شود. "
                           + "تا زمان تأیید مدیر امکان ورود وجود ندارد، پس توکنی صادر نمی‌شود")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "درخواست ثبت شد"),
        @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی"),
        @ApiResponse(responseCode = "409", description = "ایمیل یا نام فروشگاه تکراری")
    })
    public ResponseEntity<Void> registerSeller(
            @Valid @RequestBody SellerRegisterRequest request) {
        authService.registerSeller(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ==================== ورود و نشست ====================

    @PostMapping("/login")
    @Operation(summary = "ورود — مشترک برای همه نقش‌ها",
               description = "پاسخ شامل نقش کاربر است تا کلاینت پنل مناسب را نمایش دهد. "
                           + "حساب‌های در انتظار تأیید، رد شده یا تعلیق‌شده خطای ۴۰۳ می‌گیرند. "
                           + "پس از ۵ تلاش ناموفق، حساب ۱۵ دقیقه قفل می‌شود")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ورود موفق"),
        @ApiResponse(responseCode = "401", description = "ایمیل یا رمز اشتباه"),
        @ApiResponse(responseCode = "403", description = "حساب در انتظار تأیید، رد شده یا تعلیق"),
        @ApiResponse(responseCode = "429", description = "تلاش بیش از حد")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "تمدید نشست",
               description = "با refresh token، جفت توکن جدید می‌گیرید. توکن قبلی باطل می‌شود. "
                           + "استفاده مجدد از توکن باطل‌شده، همه نشست‌ها را می‌بندد")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "تمدید موفق"),
        @ApiResponse(responseCode = "401", description = "توکن نامعتبر یا منقضی"),
        @ApiResponse(responseCode = "403", description = "وضعیت حساب تغییر کرده است")
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "خروج",
               description = "همه نشست‌های فعال کاربر بسته می‌شود. "
                           + "access token فعلی تا زمان انقضا معتبر می‌ماند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "خروج موفق"),
        @ApiResponse(responseCode = "401", description = "توکن نامعتبر")
    })
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logoutEverywhere(principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ==================== بازیابی رمز ====================

    @PostMapping("/password/forgot")
    @Operation(summary = "فراموشی رمز — درخواست بازیابی",
               description = "برای کاربری که رمزش را فراموش کرده و وارد نشده است. "
                           + "توکن یک‌بارمصرف ۳۰ دقیقه‌ای ساخته می‌شود؛ "
                           + "در محیط توسعه در لاگ سرور چاپ می‌شود. "
                           + "برای جلوگیری از افشای ایمیل‌های ثبت‌شده، پاسخ همیشه یکسان است")
    @ApiResponse(responseCode = "204", description = "درخواست ثبت شد")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset")
    @Operation(summary = "فراموشی رمز — ثبت رمز جدید",
               description = "ادامه فرآیند بازیابی. با توکن دریافتی رمز جدید ثبت می‌شود. "
                           + "نیازی به رمز فعلی نیست چون کاربر آن را نمی‌داند. "
                           + "توکن پس از یک بار استفاده باطل و همه نشست‌های فعال بسته می‌شوند")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "رمز تغییر کرد"),
        @ApiResponse(responseCode = "400", description = "توکن نامعتبر، منقضی یا قبلاً استفاده‌شده")
    })
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    // ==================== پروفایل ====================

    @GetMapping("/me")
    @Operation(summary = "اطلاعات کاربر جاری",
               description = "نام، نقش، وضعیت حساب و لیست مجوزها. "
                           + "برای فروشندگان، اطلاعات فروشگاه هم برمی‌گردد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "موفق"),
        @ApiResponse(responseCode = "401", description = "توکن نامعتبر یا ارسال نشده")
    })
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(authService.getCurrentUser(principal.getId()));
    }

    @PatchMapping("/me")
    @Operation(summary = "ویرایش پروفایل",
               description = "تغییر نام و شماره موبایل",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ویرایش موفق"),
        @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی")
    })
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(principal.getId(), request));
    }

    @PatchMapping("/me/password")
    @Operation(summary = "تغییر رمز عبور — کاربر وارد شده",
               description = "برای کاربری که رمز فعلی‌اش را می‌داند و وارد شده است. "
                           + "رمز فعلی برای احراز هویت مجدد الزامی است تا اگر کسی به دستگاه "
                           + "باز کاربر دسترسی یافت، نتواند رمز را عوض کند. "
                           + "پس از تغییر، همه نشست‌های فعال باطل می‌شوند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "تغییر موفق"),
        @ApiResponse(responseCode = "400", description = "رمز فعلی اشتباه یا رمز جدید تکراری"),
        @ApiResponse(responseCode = "401", description = "توکن نامعتبر")
    })
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }
}