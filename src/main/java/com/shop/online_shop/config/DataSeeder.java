package com.shop.online_shop.config;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
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
        {"PRODUCT_READ_OWN",   "PRODUCT",  "READ_OWN",   "دیدن محصولات خود"},
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
        {"ORDER_FULFILL",      "ORDER",    "FULFILL",    "انجام سفارش — اقلام خود"},

        // آدرس
        {"ADDRESS_MANAGE",     "ADDRESS",  "MANAGE",     "مدیریت آدرس‌های خود"},
        {"ADDRESS_READ_ALL",   "ADDRESS",  "READ_ALL",   "دیدن آدرس‌های همه"},

        // پرداخت
        {"PAYMENT_READ",       "PAYMENT",  "READ",       "دیدن پرداخت‌های خود"},
        {"PAYMENT_READ_ALL",   "PAYMENT",  "READ_ALL",   "دیدن پرداخت‌های همه"},

        // کاربران
        {"USER_READ",          "USER",     "READ",       "دیدن کاربران"},
        {"USER_CREATE",        "USER",     "CREATE",     "ساخت کاربر با نقش دلخواه"},
        {"USER_MANAGE",        "USER",     "MANAGE",     "مدیریت وضعیت و نقش کاربران"},

        // نقش‌ها
        {"ROLE_READ",          "ROLE",     "READ",       "دیدن نقش‌ها و مجوزها"},
        {"ROLE_MANAGE",        "ROLE",     "MANAGE",     "ساخت و ویرایش نقش‌ها"},

        // فروشندگان
        {"SELLER_REVIEW",      "SELLER",   "REVIEW",     "بررسی و مدیریت فروشندگان"},

        // ممیزی
        {"AUDIT_READ",         "AUDIT",    "READ",       "مشاهده گزارش رویدادها"},
    };

    /** permissions == null یعنی همه مجوزها */
    private record BaseRole(String code, String name, String description,
                            boolean sellerApproval, boolean openRegistration,
                            List<String> permissions) {
    }

    private static final List<BaseRole> BASE_ROLES = List.of(

        new BaseRole(RoleCode.USER, "کاربر عادی",
                "مشتری سامانه — خرید و مدیریت حساب خود",
                false, true,
                List.of("PRODUCT_READ", "CATEGORY_READ", "CART_MANAGE",
                        "ORDER_CREATE", "ORDER_READ", "ADDRESS_MANAGE", "PAYMENT_READ")),

        new BaseRole(RoleCode.SELLER, "فروشنده",
                "عرضه محصول و انجام سفارش‌های مربوط به خود",
                true, true,
                List.of("PRODUCT_READ", "PRODUCT_READ_OWN", "PRODUCT_CREATE",
                        "PRODUCT_UPDATE", "PRODUCT_DELETE", "CATEGORY_READ",
                        "ORDER_READ", "ORDER_FULFILL", "ADDRESS_MANAGE", "PAYMENT_READ")),

        new BaseRole(RoleCode.MANAGER, "مدیر",
                "مدیریت کاتالوگ، سفارش‌ها و فروشندگان",
                false, false,
                List.of("PRODUCT_READ", "PRODUCT_READ_OWN", "PRODUCT_CREATE",
                        "PRODUCT_UPDATE", "PRODUCT_DELETE", "PRODUCT_MANAGE_ALL",
                        "CATEGORY_READ", "CATEGORY_MANAGE", "CART_VIEW_ALL",
                        "ORDER_READ", "ORDER_READ_ALL", "ORDER_UPDATE", "ORDER_FULFILL",
                        "ADDRESS_MANAGE", "ADDRESS_READ_ALL",
                        "PAYMENT_READ", "PAYMENT_READ_ALL",
                        "USER_READ", "ROLE_READ", "SELLER_REVIEW", "AUDIT_READ")),

        new BaseRole(RoleCode.ADMIN, "ادمین کل",
                "دسترسی کامل به تمام بخش‌های سامانه",
                false, false,
                null)
    );

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Permission> permissions = syncPermissions();
        seedBaseRoles(permissions);
    }

    /** مجوزهای تازه اضافه می‌شوند؛ موجودها دست‌نخورده می‌مانند */
    private Map<String, Permission> syncPermissions() {
        Map<String, Permission> existing = new HashMap<>();
        permissionRepository.findAll().forEach(p -> existing.put(p.getName(), p));

        int created = 0;

        for (String[] row : PERMISSIONS) {
            if (existing.containsKey(row[0])) {
                continue;
            }
            Permission saved = permissionRepository.save(Permission.builder()
                    .name(row[0])
                    .resource(row[1])
                    .action(row[2])
                    .description(row[3])
                    .build());
            existing.put(row[0], saved);
            created++;
        }

        if (created > 0) {
            log.info("Created {} new permissions (total {})", created, existing.size());
        }
        return existing;
    }

    /**
     * نقش‌های پایه تنها در نخستین اجرا ساخته می‌شوند.
     * پس از آن مجوزهایشان در اختیار مدیر سامانه است و seeder
     * تغییرات او را بازنویسی نمی‌کند.
     */
    private void seedBaseRoles(Map<String, Permission> permissions) {
        for (BaseRole base : BASE_ROLES) {

            if (roleRepository.existsByCode(base.code())) {
                continue;
            }

            Set<Permission> perms = base.permissions() == null
                    ? new HashSet<>(permissions.values())
                    : resolve(base.permissions(), permissions, base.code());

            roleRepository.save(Role.builder()
                    .code(base.code())
                    .name(base.name())
                    .description(base.description())
                    .permissions(perms)
                    .systemRole(true)
                    .requiresSellerApproval(base.sellerApproval())
                    .openRegistration(base.openRegistration())
                    .build());

            log.info("Created base role {} with {} permissions", base.code(), perms.size());
        }
    }

    private Set<Permission> resolve(List<String> names,
                                    Map<String, Permission> pool,
                                    String roleCode) {
        Set<Permission> result = new HashSet<>();

        for (String name : names) {
            Permission permission = pool.get(name);

            if (permission == null) {
                log.warn("Role {} references unknown permission {}", roleCode, name);
                continue;
            }
            result.add(permission);
        }
        return result;
    }
}