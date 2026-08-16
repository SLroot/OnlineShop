package com.shop.online_shop.config;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.repository.PermissionRepository;
import com.shop.online_shop.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    /** نام یکتا، منبع، عملیات، توضیح */
    private static final String[][] PERMISSIONS = {
        // محصولات
        {"PRODUCT_READ",       "PRODUCT",  "READ",       "دیدن محصولات"},
        {"PRODUCT_READ_OWN",   "PRODUCT",  "READ_OWN",   "دیدن محصولات خود فروشنده"},
        {"PRODUCT_CREATE",     "PRODUCT",  "CREATE",     "افزودن محصول"},
        {"PRODUCT_UPDATE",     "PRODUCT",  "UPDATE",     "ویرایش محصول"},
        {"PRODUCT_DELETE",     "PRODUCT",  "DELETE",     "حذف محصول"},
        {"PRODUCT_MANAGE_ALL", "PRODUCT",  "MANAGE_ALL", "مدیریت محصولات همه فروشندگان"},

        // دسته‌بندی
        {"CATEGORY_READ",      "CATEGORY", "READ",       "دیدن دسته‌بندی‌ها"},
        {"CATEGORY_MANAGE",    "CATEGORY", "MANAGE",     "مدیریت دسته‌بندی‌ها"},

        // سبد خرید
        {"CART_MANAGE",        "CART",     "MANAGE",     "مدیریت سبد خرید خود"},
        {"CART_VIEW_ALL",      "CART",     "VIEW_ALL",   "مشاهده سبد خرید کاربران"},

        // سفارش
        {"ORDER_CREATE",       "ORDER",    "CREATE",     "ثبت سفارش"},
        {"ORDER_READ",         "ORDER",    "READ",       "دیدن سفارش‌های خود"},
        {"ORDER_READ_ALL",     "ORDER",    "READ_ALL",   "دیدن سفارش‌های همه"},
        {"ORDER_UPDATE",       "ORDER",    "UPDATE",     "تغییر وضعیت هر سفارش"},
        {"ORDER_FULFILL",      "ORDER",    "FULFILL",    "انجام سفارش — تغییر وضعیت اقلام خود"},

        // آدرس
        {"ADDRESS_MANAGE",     "ADDRESS",  "MANAGE",     "مدیریت آدرس‌های خود"},
        {"ADDRESS_READ_ALL",   "ADDRESS",  "READ_ALL",   "دیدن آدرس‌های همه"},

        // نظرات
        {"REVIEW_CREATE",      "REVIEW",   "CREATE",     "ثبت نظر"},
        {"REVIEW_MODERATE",    "REVIEW",   "MODERATE",   "مدیریت نظرات"},

        // پرداخت
        {"PAYMENT_READ",       "PAYMENT",  "READ",       "دیدن پرداخت‌های خود"},
        {"PAYMENT_READ_ALL",   "PAYMENT",  "READ_ALL",   "دیدن پرداخت‌های همه"},

        // کاربران و نقش‌ها
        {"USER_READ",          "USER",     "READ",       "دیدن کاربران"},
        {"USER_MANAGE",        "USER",     "MANAGE",     "مدیریت کاربران"},
        {"ROLE_READ",          "ROLE",     "READ",       "دیدن نقش‌ها"},
        {"ROLE_MANAGE",        "ROLE",     "MANAGE",     "مدیریت نقش‌ها"},

        // ممیزی
        {"AUDIT_READ",         "AUDIT",    "READ",       "مشاهده گزارش رویدادها"},

        // فروشندگان
        {"SELLER_REVIEW",      "SELLER",   "REVIEW",     "بررسی و مدیریت فروشندگان"},
    };

    private static final Map<String, List<String>> ROLE_PERMISSIONS = buildRoleMap();

    private static final Map<String, String> ROLE_DESCRIPTIONS = Map.of(
        "USER",    "کاربر عادی",
        "SELLER",  "فروشنده",
        "MANAGER", "مدیر",
        "ADMIN",   "ادمین کل"
    );

    private static Map<String, List<String>> buildRoleMap() {
        Map<String, List<String>> map = new LinkedHashMap<>();

        // مشتری — تنها نقشی که خرید می‌کند
        map.put("USER", List.of(
            "PRODUCT_READ",
            "CATEGORY_READ",
            "CART_MANAGE",
            "ORDER_CREATE", "ORDER_READ",
            "ADDRESS_MANAGE",
            "REVIEW_CREATE",
            "PAYMENT_READ"
        ));

        // فروشنده — بدون CART_MANAGE و ORDER_CREATE چون خرید نمی‌کند
        map.put("SELLER", List.of(
            "PRODUCT_READ", "PRODUCT_READ_OWN", "PRODUCT_CREATE",
            "PRODUCT_UPDATE", "PRODUCT_DELETE",
            "CATEGORY_READ",
            "ORDER_READ", "ORDER_FULFILL",
            "ADDRESS_MANAGE",
            "PAYMENT_READ"
        ));

        // مدیر — بدون CART_MANAGE و ORDER_CREATE
        map.put("MANAGER", List.of(
            "PRODUCT_READ", "PRODUCT_READ_OWN", "PRODUCT_CREATE",
            "PRODUCT_UPDATE", "PRODUCT_DELETE", "PRODUCT_MANAGE_ALL",
            "CATEGORY_READ", "CATEGORY_MANAGE",
            "CART_VIEW_ALL",
            "ORDER_READ", "ORDER_READ_ALL", "ORDER_UPDATE", "ORDER_FULFILL",
            "ADDRESS_MANAGE", "ADDRESS_READ_ALL",
            "REVIEW_MODERATE",
            "PAYMENT_READ", "PAYMENT_READ_ALL",
            "USER_READ", "ROLE_READ",
            "AUDIT_READ",
            "SELLER_REVIEW"
        ));

        return map;
    }

    /**
     * قابل اجرای مکرر: مجوزها و نقش‌های موجود دوباره ساخته نمی‌شوند،
     * ولی موارد جدید به دیتابیس اضافه می‌گردند.
     */
    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Permission> permissions = syncPermissions();
        syncRoles(permissions);
    }

    private Map<String, Permission> syncPermissions() {
        Map<String, Permission> existing = new HashMap<>();
        permissionRepository.findAll().forEach(p -> existing.put(p.getName(), p));

        int created = 0;

        for (String[] row : PERMISSIONS) {
            String name = row[0];

            if (existing.containsKey(name)) {
                continue;
            }

            Permission saved = permissionRepository.save(Permission.builder()
                    .name(name)
                    .resource(row[1])
                    .action(row[2])
                    .description(row[3])
                    .build());

            existing.put(name, saved);
            created++;
        }

        if (created > 0) {
            log.info("Created {} new permissions (total: {})", created, existing.size());
        }
        return existing;
    }

    private void syncRoles(Map<String, Permission> permissions) {
        ROLE_PERMISSIONS.forEach((roleName, permissionNames) -> {
            Set<Permission> perms = new HashSet<>();

            for (String permissionName : permissionNames) {
                Permission permission = permissions.get(permissionName);

                if (permission == null) {
                    log.warn("Permission {} referenced by role {} does not exist",
                            permissionName, roleName);
                    continue;
                }
                perms.add(permission);
            }

            upsertRole(roleName, perms);
        });

        // ADMIN همه مجوزها را دارد
        upsertRole("ADMIN", new HashSet<>(permissions.values()));
    }

    private void upsertRole(String name, Set<Permission> permissions) {
        Role role = roleRepository.findByName(name).orElse(null);

        if (role == null) {
            roleRepository.save(Role.builder()
                    .name(name)
                    .description(ROLE_DESCRIPTIONS.get(name))
                    .permissions(permissions)
                    .build());

            log.info("Created role {} with {} permissions", name, permissions.size());
            return;
        }

        int before = role.getPermissions().size();
        role.getPermissions().addAll(permissions);
        int after = role.getPermissions().size();

        if (after > before) {
            roleRepository.save(role);
            log.info("Role {} updated: {} -> {} permissions", name, before, after);
        }
    }
}