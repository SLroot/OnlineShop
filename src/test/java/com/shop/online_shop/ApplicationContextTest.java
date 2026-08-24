package com.shop.online_shop;

import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("راه‌اندازی اولیه سامانه")
class ApplicationContextTest extends BaseIntegrationTest {

    @Test
    @DisplayName("چهار نقش پایه با کد ماشینی ساخته می‌شوند")
    void baseRolesAreSeeded() {
        for (String code : new String[]{RoleCode.USER, RoleCode.SELLER,
                                        RoleCode.MANAGER, RoleCode.ADMIN}) {
            assertThat(roleRepository.findByCode(code))
                    .as("نقش %s", code)
                    .isPresent();
        }
    }

    @Test
    @DisplayName("نقش‌های پایه به‌عنوان نقش سامانه علامت خورده‌اند")
    void baseRolesAreMarkedAsSystem() {
        roleRepository.findAll().forEach(role ->
                assertThat(role.isSystemRole())
                        .as("نقش %s", role.getName())
                        .isTrue());
    }

    @Test
    @DisplayName("تنها نقش فروشنده پرچم تأیید دارد")
    void onlySellerRequiresApproval() {
        assertThat(role(RoleCode.SELLER).isRequiresSellerApproval()).isTrue();
        assertThat(role(RoleCode.USER).isRequiresSellerApproval()).isFalse();
        assertThat(role(RoleCode.MANAGER).isRequiresSellerApproval()).isFalse();
        assertThat(role(RoleCode.ADMIN).isRequiresSellerApproval()).isFalse();
    }

    @Test
    @DisplayName("تنها نقش مشتری و فروشنده در ثبت‌نام آزادند")
    void onlyCustomerAndSellerAreOpenForRegistration() {
        assertThat(role(RoleCode.USER).isOpenRegistration()).isTrue();
        assertThat(role(RoleCode.SELLER).isOpenRegistration()).isTrue();
        assertThat(role(RoleCode.MANAGER).isOpenRegistration()).isFalse();
        assertThat(role(RoleCode.ADMIN).isOpenRegistration()).isFalse();
    }

    @Test
    @DisplayName("ادمین همه مجوزها را دارد")
    void adminHasAllPermissions() {
        long total = permissionRepository.count();
        assertThat(role(RoleCode.ADMIN).getPermissions()).hasSize((int) total);
    }

    @Test
    @DisplayName("تنها مشتری مجوز خرید دارد")
    void onlyCustomerCanBuy() {
        assertThat(permissionNames(role(RoleCode.USER)))
                .contains("CART_MANAGE", "ORDER_CREATE");

        assertThat(permissionNames(role(RoleCode.SELLER)))
                .doesNotContain("CART_MANAGE", "ORDER_CREATE");

        assertThat(permissionNames(role(RoleCode.MANAGER)))
                .doesNotContain("CART_MANAGE", "ORDER_CREATE");
    }

    @Test
    @DisplayName("نقش سفارشی بدون کد ماشینی ساخته می‌شود")
    void customRoleHasNoCode() {
        Role custom = createCustomRole("نقش آزمایشی", "PRODUCT_READ");

        assertThat(custom.getCode()).isNull();
        assertThat(custom.isCustom()).isTrue();
        assertThat(custom.isSystemRole()).isFalse();
    }

    @Test
    @DisplayName("مسیر عمومی محصولات بدون توکن در دسترس است")
    void publicCatalogIsAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("مسیر محافظت‌شده بدون توکن ۴۰۱ می‌دهد")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    private Role role(String code) {
        return roleRepository.findByCode(code).orElseThrow();
    }

    private Set<String> permissionNames(Role role) {
        return role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }
}