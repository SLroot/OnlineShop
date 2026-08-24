package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.RoleRequest;
import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.PermissionRepository;
import com.shop.online_shop.repository.RoleRepository;
import com.shop.online_shop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;

    // ==================== خواندن ====================

    @Transactional(readOnly = true)
    public List<Role> listAll() {
        return roleRepository.findAll().stream()
                .sorted(Comparator.comparing(Role::getId))
                .toList();
    }

    @Transactional(readOnly = true)
    public Role getById(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> ApiException.notFound("نقش یافت نشد"));
    }

    @Transactional(readOnly = true)
    public long countUsers(Long roleId) {
        return roleRepository.countUsers(roleId);
    }

    @Transactional(readOnly = true)
    public List<Permission> listPermissions() {
        return permissionRepository.findAll().stream()
                .sorted(Comparator.comparing(Permission::getName))
                .toList();
    }

    // ==================== ساخت و ویرایش ====================

    @Transactional
    public Role create(RoleRequest req, UserPrincipal me) {
        String name = req.name().trim();

        if (roleRepository.existsByName(name)) {
            throw ApiException.conflict("نقشی با این نام قبلاً وجود دارد");
        }

        Set<Permission> permissions = resolvePermissions(req.permissionIds());
        assertCanGrant(permissions, me);

        Role role = roleRepository.save(Role.builder()
                .code(null)                     // نقش سفارشی — بدون شناسه ماشینی
                .name(name)
                .description(req.description())
                .permissions(permissions)
                .systemRole(false)
                .requiresSellerApproval(req.requiresSellerApproval())
                .openRegistration(false)        // ثبت‌نام آزاد فقط برای نقش‌های پایه
                .build());

        auditLogService.record(me.getId(), "ROLE_CREATED",
                "role: " + role.getId() + " | " + name
                        + " | permissions: " + permissions.size());

        return role;
    }

    /**
     * ویرایش نقش. نام و توضیح همیشه قابل تغییرند حتی برای نقش پایه؛
     * مجموعه مجوزها با فهرست ارسالی جایگزین می‌شود.
     */
    @Transactional
    public Role update(Long roleId, RoleRequest req, UserPrincipal me) {
        Role role = getById(roleId);
        String name = req.name().trim();

        if (roleRepository.existsByNameAndIdNot(name, roleId)) {
            throw ApiException.conflict("نقشی با این نام قبلاً وجود دارد");
        }

        Set<Permission> requested = resolvePermissions(req.permissionIds());

        // فقط مجوزهایی که واقعاً تغییر می‌کنند بررسی می‌شوند،
        // تا ویرایش نام یک نقش پرمجوز به‌خاطر مجوزهای موجود رد نشود
        Set<Permission> added = difference(requested, role.getPermissions());
        Set<Permission> removed = difference(role.getPermissions(), requested);

        assertCanGrant(added, me);
        assertCanGrant(removed, me);

        assertNotLockingOutAdmin(role, requested);

        role.setName(name);
        role.setDescription(req.description());

        if (!role.isSystemRole()) {
            role.setRequiresSellerApproval(req.requiresSellerApproval());
        }

        role.getPermissions().clear();
        role.getPermissions().addAll(requested);

        Role saved = roleRepository.save(role);

        if (!added.isEmpty() || !removed.isEmpty()) {
            revokeSessionsOfRole(role, me.getId(),
                    "added: " + names(added) + " | removed: " + names(removed));
        }

        return saved;
    }

    @Transactional
    public void delete(Long roleId, UserPrincipal me) {
        Role role = getById(roleId);

        if (role.isSystemRole()) {
            throw ApiException.forbidden("نقش‌های پایه سامانه قابل حذف نیستند");
        }

        long users = roleRepository.countUsers(roleId);
        if (users > 0) {
            throw ApiException.conflict(
                    users + " کاربر این نقش را دارند — ابتدا نقش آن‌ها را تغییر دهید");
        }

        roleRepository.delete(role);
        auditLogService.record(me.getId(), "ROLE_DELETED",
                "role: " + roleId + " | " + role.getName());
    }

    // ==================== مجوز تکی ====================

    @Transactional
    public Role grantPermission(Long roleId, Long permissionId, UserPrincipal me) {
        Role role = getById(roleId);
        Permission permission = requirePermission(permissionId);

        assertCanGrant(Set.of(permission), me);

        if (!role.getPermissions().add(permission)) {
            throw ApiException.badRequest("این نقش قبلاً این مجوز را دارد");
        }

        Role saved = roleRepository.save(role);
        revokeSessionsOfRole(role, me.getId(), "granted " + permission.getName());

        return saved;
    }

    @Transactional
    public Role revokePermission(Long roleId, Long permissionId, UserPrincipal me) {
        Role role = getById(roleId);
        Permission permission = requirePermission(permissionId);

        assertCanGrant(Set.of(permission), me);

        Set<Permission> remaining = role.getPermissions().stream()
                .filter(p -> !p.getId().equals(permissionId))
                .collect(Collectors.toSet());

        if (remaining.size() == role.getPermissions().size()) {
            throw ApiException.badRequest("این نقش چنین مجوزی ندارد");
        }

        assertNotLockingOutAdmin(role, remaining);

        role.getPermissions().clear();
        role.getPermissions().addAll(remaining);

        Role saved = roleRepository.save(role);
        revokeSessionsOfRole(role, me.getId(), "revoked " + permission.getName());

        return saved;
    }

    // ==================== محافظت ====================

    /**
     * هیچ‌کس نمی‌تواند مجوزی را اعطا یا سلب کند که خودش ندارد.
     * بدون این قاعده، دارنده ROLE_MANAGE می‌توانست نقشی با تمام
     * مجوزها بسازد و به خودش بدهد.
     */
    private void assertCanGrant(Set<Permission> permissions, UserPrincipal me) {
        List<String> missing = permissions.stream()
                .map(Permission::getName)
                .filter(name -> !me.hasAuthority(name))
                .sorted()
                .toList();

        if (!missing.isEmpty()) {
            throw ApiException.forbidden(
                    "نمی‌توانید مجوزی را تنظیم کنید که خودتان ندارید: "
                            + String.join("، ", missing));
        }
    }

    /**
     * نقش ادمین باید همیشه بتواند نقش‌ها را مدیریت کند،
     * وگرنه سامانه بدون راه بازگشت قفل می‌شود.
     */
    private void assertNotLockingOutAdmin(Role role, Set<Permission> newPermissions) {
        if (!RoleCode.ADMIN.equals(role.getCode())) {
            return;
        }

        boolean keepsRoleManage = newPermissions.stream()
                .anyMatch(p -> "ROLE_MANAGE".equals(p.getName()));

        if (!keepsRoleManage) {
            throw ApiException.badRequest(
                    "مجوز ROLE_MANAGE را نمی‌توان از نقش ادمین برداشت");
        }
    }

    /**
     * پس از تغییر مجوزها نشست‌های همه دارندگان آن نقش باطل می‌شود،
     * چون مجوزها درون توکن دسترسی قرار دارند.
     */
    private void revokeSessionsOfRole(Role role, Long actorId, String detail) {
        List<Long> userIds = roleRepository.findUserIds(role.getId());
        userIds.forEach(refreshTokenService::revokeAllForUser);

        auditLogService.record(actorId, "ROLE_PERMISSIONS_CHANGED",
                role.getName() + " | " + detail + " | sessions revoked: " + userIds.size());

        log.info("Role {} permissions changed, revoked sessions for {} users",
                role.getName(), userIds.size());
    }

    // ==================== کمکی ====================

    private Set<Permission> resolvePermissions(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new HashSet<>();
        }

        List<Permission> found = permissionRepository.findAllById(ids);

        if (found.size() != ids.size()) {
            throw ApiException.badRequest("یک یا چند مجوز ارسالی نامعتبر است");
        }
        return new HashSet<>(found);
    }

    private Permission requirePermission(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("مجوز یافت نشد"));
    }

    private Set<Permission> difference(Set<Permission> from, Set<Permission> minus) {
        Set<Long> excluded = minus.stream().map(Permission::getId).collect(Collectors.toSet());
        return from.stream()
                .filter(p -> !excluded.contains(p.getId()))
                .collect(Collectors.toSet());
    }

    private String names(Set<Permission> permissions) {
        return permissions.isEmpty() ? "-" : permissions.stream()
                .map(Permission::getName).sorted().collect(Collectors.joining(", "));
    }
}