package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.ChangePasswordRequest;
import com.shop.online_shop.dto.request.LoginRequest;
import com.shop.online_shop.dto.request.RegisterRequest;
import com.shop.online_shop.dto.request.SellerRegisterRequest;
import com.shop.online_shop.dto.request.UpdateProfileRequest;
import com.shop.online_shop.dto.response.AuthResponse;
import com.shop.online_shop.dto.response.UserResponse;
import com.shop.online_shop.entity.RefreshToken;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.SellerProfile;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.RoleRepository;
import com.shop.online_shop.repository.SellerProfileRepository;
import com.shop.online_shop.repository.UserRepository;
import com.shop.online_shop.security.JwtService;
import com.shop.online_shop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditLogService auditLogService;
    private final CartService cartService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // ==================== ثبت‌نام ====================

    @Transactional
    public AuthResponse registerCustomer(RegisterRequest req) {
        String email = normalize(req.email());
        assertEmailAvailable(email);

        Role role = requireRoleByCode(RoleCode.USER);

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .phone(req.phone())
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());

        cartService.createFor(user);

        auditLogService.record(user.getId(), "CUSTOMER_REGISTERED", null);
        return issueTokens(user);
    }

    /**
     * ثبت‌نام فروشنده.
     * وضعیت اولیه از روی پرچم نقش تعیین می‌شود نه نام آن، بنابراین اگر
     * مدیر سامانه پرچم تأیید را از نقش بردارد، ثبت‌نام بدون انتظار انجام می‌شود.
     */
    @Transactional
    public void registerSeller(SellerRegisterRequest req) {
        String email = normalize(req.email());
        assertEmailAvailable(email);

        String shopName = req.shopName().trim();
        if (sellerProfileRepository.existsByShopName(shopName)) {
            throw ApiException.conflict("این نام فروشگاه قبلاً ثبت شده است");
        }

        Role role = requireRoleByCode(RoleCode.SELLER);

        UserStatus status = role.isRequiresSellerApproval()
                ? UserStatus.PENDING
                : UserStatus.ACTIVE;

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .fullName(req.fullName().trim())
                .phone(req.phone())
                .role(role)
                .status(status)
                .build());

        sellerProfileRepository.save(SellerProfile.builder()
                .user(user)
                .shopName(shopName)
                .landline(req.landline())
                .build());

        auditLogService.record(user.getId(), "SELLER_REGISTERED", "shop: " + shopName);
    }

    // ==================== ورود ====================

    @Transactional
    public AuthResponse login(LoginRequest req) {
        String email = normalize(req.email());
        loginAttemptService.assertNotBlocked(email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPassword())) {
            loginAttemptService.recordFailure(email);
            auditLogService.record(user != null ? user.getId() : null,
                    "LOGIN_FAILED", "email: " + email);
            throw ApiException.unauthorized("ایمیل یا رمز عبور اشتباه است");
        }

        assertCanLogin(user);

        loginAttemptService.recordSuccess(email);
        auditLogService.record(user.getId(), "LOGIN_SUCCESS",
                "role: " + user.getRole().getName());

        return issueTokens(user);
    }

    /** پیام دقیق بر اساس وضعیت حساب */
    private void assertCanLogin(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> { }

            case PENDING -> throw ApiException.forbidden(
                    "درخواست شما در انتظار بررسی است");

            case REJECTED -> {
                String reason = user.getSellerProfile() != null
                        ? user.getSellerProfile().getRejectionReason() : null;
                throw ApiException.forbidden("درخواست شما رد شده است"
                        + (reason != null ? " — " + reason : ""));
            }

            case SUSPENDED -> {
                String reason = user.getSellerProfile() != null
                        ? user.getSellerProfile().getSuspensionReason() : null;
                throw ApiException.forbidden("حساب شما تعلیق شده است"
                        + (reason != null ? " — " + reason : ""));
            }
        }
    }

    // ==================== نشست ====================

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken rotated = refreshTokenService.rotate(refreshToken);
        User user = rotated.getUser();

        // وضعیت ممکن است بین دو درخواست تغییر کرده باشد
        assertCanLogin(user);

        String access = jwtService.generateAccessToken(UserPrincipal.from(user));
        return AuthResponse.of(access, rotated.getToken(),
                jwtService.getAccessExpirationSeconds(), UserResponse.from(user));
    }

    @Transactional
    public void logoutEverywhere(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
        auditLogService.record(userId, "LOGOUT", null);
    }

    // ==================== پروفایل ====================

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = requireUser(userId);

        user.setFullName(req.fullName().trim());
        user.setPhone(req.phone());

        auditLogService.record(userId, "PROFILE_UPDATED", null);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = requireUser(userId);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPassword())) {
            throw ApiException.badRequest("رمز فعلی اشتباه است");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPassword())) {
            throw ApiException.badRequest("رمز جدید نباید با رمز فعلی یکسان باشد");
        }

        user.setPassword(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(userId);
        auditLogService.record(userId, "PASSWORD_CHANGED", null);
    }

    // ==================== کمکی ====================

    private AuthResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(UserPrincipal.from(user));
        RefreshToken refresh = refreshTokenService.issue(user);

        return AuthResponse.of(access, refresh.getToken(),
                jwtService.getAccessExpirationSeconds(), UserResponse.from(user));
    }

    private void assertEmailAvailable(String email) {
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("این ایمیل قبلاً ثبت شده است");
        }
    }

    private Role requireRoleByCode(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> ApiException.badRequest("نقش پایه " + code + " یافت نشد"));
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));
    }

    private String normalize(String email) {
        return email.toLowerCase().trim();
    }
}