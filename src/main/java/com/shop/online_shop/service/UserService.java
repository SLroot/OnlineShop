package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.CreateUserRequest;
import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.RoleRepository;
import com.shop.online_shop.repository.UserRepository;
import com.shop.online_shop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenService refreshTokenService;
    private final CartService cartService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    // ==================== خواندن ====================

    @Transactional(readOnly = true)
    public Page<User> list(Long roleId, UserStatus status, Pageable pageable) {
        if (roleId != null && status != null) {
            return userRepository.findByRoleIdAndStatus(roleId, status, pageable);
        }
        if (roleId != null) {
            return userRepository.findByRoleId(roleId, pageable);
        }
        if (status != null) {
            return userRepository.findByStatus(status, pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("کاربر یافت نشد"));
    }

    // ==================== ساخت ====================

    /**
     * ساخت کاربر با نقش دلخواه.
     * رمز اولیه توسط سازنده تعیین می‌شود و کاربر در نخستین ورود
     * موظف به تغییر آن است.
     */
    @Transactional
    public User create(CreateUserRequest req, UserPrincipal me) {
        String email = req.email().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("این ایمیل قبلاً ثبت شده است");
        }

        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> ApiException.badRequest("نقش انتخاب‌شده یافت نشد"));

        assertCanAssignRole(role, me);

        User user = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.initialPassword()))
                .fullName(req.fullName().trim())
                .phone(req.phone())
                .role(role)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)
                .build());

        // نقش‌هایی که امکان خرید دارند به سبد نیاز دارند
        if (role.hasPermission("CART_MANAGE")) {
            cartService.createFor(user);
        }

        auditLogService.record(me.getId(), "USER_CREATED",
                "user: " + user.getId() + " | role: " + role.getName());

        return user;
    }

    // ==================== تغییر نقش و وضعیت ====================

    @Transactional
    public User changeRole(Long userId, Long roleId, UserPrincipal me) {
        User user = getById(userId);

        if (user.getId().equals(me.getId())) {
            throw ApiException.badRequest("نمی‌توانید نقش خودتان را تغییر دهید");
        }

        Role newRole = roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.badRequest("نقش انتخاب‌شده یافت نشد"));

        assertCanAssignRole(newRole, me);
        assertNotLastAdmin(user, "تنزل");

        String previous = user.getRole().getName();
        user.setRole(newRole);

        // مجوزها درون توکن هستند، پس نشست‌ها باید بسته شوند
        refreshTokenService.revokeAllForUser(userId);

        auditLogService.record(me.getId(), "USER_ROLE_CHANGED",
                "user: " + userId + " | " + previous + " -> " + newRole.getName());

        return userRepository.save(user);
    }

    @Transactional
    public User suspend(Long userId, UserPrincipal me) {
        User user = getById(userId);

        if (user.getId().equals(me.getId())) {
            throw ApiException.badRequest("نمی‌توانید حساب خودتان را تعلیق کنید");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.badRequest("این حساب قبلاً تعلیق شده است");
        }

        assertNotLastAdmin(user, "تعلیق");

        user.setStatus(UserStatus.SUSPENDED);
        refreshTokenService.revokeAllForUser(userId);

        auditLogService.record(me.getId(), "USER_SUSPENDED", "user: " + userId);
        return userRepository.save(user);
    }

    @Transactional
    public User activate(Long userId, UserPrincipal me) {
        User user = getById(userId);

        user.setStatus(UserStatus.ACTIVE);
        auditLogService.record(me.getId(), "USER_ACTIVATED", "user: " + userId);

        return userRepository.save(user);
    }

    // ==================== محافظت ====================

    /**
     * همان قاعده ارتقای دسترسی که در مدیریت نقش‌ها اعمال شد:
     * کسی نمی‌تواند نقشی را به کاربری بدهد که مجوزهایش فراتر از
     * دسترسی خودش باشد.
     */
    private void assertCanAssignRole(Role role, UserPrincipal me) {
        List<String> beyond = role.getPermissions().stream()
                .map(Permission::getName)
                .filter(name -> !me.hasAuthority(name))
                .sorted()
                .toList();

        if (!beyond.isEmpty()) {
            throw ApiException.forbidden(
                    "این نقش مجوزهایی دارد که خودتان ندارید: " + String.join("، ", beyond));
        }
    }

    /** آخرین ادمین فعال نباید تنزل یا تعلیق شود */
    private void assertNotLastAdmin(User user, String action) {
        boolean isAdmin = RoleCode.ADMIN.equals(user.getRole().getCode());

        if (isAdmin && userRepository.countActiveAdmins() <= 1) {
            throw ApiException.badRequest(
                    "این تنها ادمین فعال سامانه است و " + action + " آن ممکن نیست");
        }
    }
}