package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.CreateManagerRequest;
import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.PermissionRepository;
import com.shop.online_shop.repository.RoleRepository;
import com.shop.online_shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    /** فقط مجوزهای این نقش‌ها قابل ویرایش است */
    private static final Set<String> EDITABLE_ROLES = Set.of("MANAGER", "SELLER");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

    // ==================== مدیران ====================

    @Transactional
    public User createManager(CreateManagerRequest req, Long actorId) {
        String email = req.email().toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("این ایمیل قبلاً ثبت شده است");
        }

        Role managerRole = roleRepository.findByName("MANAGER")
                .orElseThrow(() -> ApiException.badRequest("نقش MANAGER یافت نشد"));

        User manager = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(req.initialPassword()))
                .fullName(req.fullName().trim())
                .phone(req.phone())
                .role(managerRole)
                .status(UserStatus.ACTIVE)
                .mustChangePassword(true)    // در اولین ورود باید عوض کند
                .build());

        auditLogService.record(actorId, "MANAGER_CREATED", "manager: " + manager.getId());
        return manager;
    }

    @Transactional
    public User suspendManager(Long managerId, Long actorId) {
        User manager = requireManager(managerId);

        if (manager.getStatus() == UserStatus.SUSPENDED) {
            throw ApiException.badRequest("این مدیر قبلاً تعلیق شده است");
        }

        manager.setStatus(UserStatus.SUSPENDED);
        refreshTokenService.revokeAllForUser(managerId);

        auditLogService.record(actorId, "MANAGER_SUSPENDED", "manager: " + managerId);
        return userRepository.save(manager);
    }

    @Transactional
    public User activateManager(Long managerId, Long actorId) {
        User manager = requireManager(managerId);

        manager.setStatus(UserStatus.ACTIVE);
        auditLogService.record(actorId, "MANAGER_ACTIVATED", "manager: " + managerId);
        return userRepository.save(manager);
    }

    // ==================== مجوزهای نقش ====================

    @Transactional(readOnly = true)
    public List<Role> listRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAll();
    }

    public boolean isEditable(Role role) {
        return EDITABLE_ROLES.contains(role.getName());
    }

    @Transactional
    public Role grantPermission(Long roleId, Long permissionId, Long actorId) {
        Role role = requireEditableRole(roleId);

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> ApiException.notFound("مجوز یافت نشد"));

        if (!role.getPermissions().add(permission)) {
            throw ApiException.badRequest("این نقش قبلاً این مجوز را دارد");
        }

        Role saved = roleRepository.save(role);
        revokeSessionsOfRole(role, actorId, "GRANTED " + permission.getName());
        return saved;
    }

    @Transactional
    public Role revokePermission(Long roleId, Long permissionId, Long actorId) {
        Role role = requireEditableRole(roleId);

        boolean removed = role.getPermissions()
                .removeIf(p -> p.getId().equals(permissionId));

        if (!removed) {
            throw ApiException.badRequest("این نقش چنین مجوزی ندارد");
        }

        Role saved = roleRepository.save(role);
        revokeSessionsOfRole(role, actorId, "REVOKED permission " + permissionId);
        return saved;
    }

    /**
     * پس از تغییر مجوزها، نشست‌های همه کاربران آن نقش باطل می‌شود
     * تا مجوزهای قدیمی داخل توکن‌های فعال باقی نماند.
     */
    private void revokeSessionsOfRole(Role role, Long actorId, String detail) {
        List<Long> userIds = userRepository.findIdsByRoleId(role.getId());
        userIds.forEach(refreshTokenService::revokeAllForUser);

        auditLogService.record(actorId, "ROLE_PERMISSIONS_CHANGED",
                role.getName() + " | " + detail + " | sessions revoked: " + userIds.size());

        log.info("Role {} permissions changed, revoked sessions for {} users",
                role.getName(), userIds.size());
    }

    // ==================== کمکی ====================

    private Role requireEditableRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("نقش یافت نشد"));

        if (!isEditable(role)) {
            throw ApiException.forbidden(
                    "مجوزهای نقش " + role.getName() + " قابل تغییر نیست");
        }
        return role;
    }

    private User requireManager(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("مدیر یافت نشد"));

        if (!"MANAGER".equals(user.getRole().getName())) {
            throw ApiException.badRequest("این کاربر مدیر نیست");
        }
        return user;
    }
}