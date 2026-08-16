package com.shop.online_shop;

import com.shop.online_shop.repository.PermissionRepository;
import com.shop.online_shop.repository.RoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("راه‌اندازی اولیه برنامه")
class ApplicationContextTest extends BaseIntegrationTest {

    @Autowired RoleRepository roleRepository;
    @Autowired PermissionRepository permissionRepository;

    @Test
    @DisplayName("چهار نقش اصلی seed می‌شوند")
    void rolesAreSeeded() {
        assertThat(roleRepository.findByName("USER")).isPresent();
        assertThat(roleRepository.findByName("SELLER")).isPresent();
        assertThat(roleRepository.findByName("MANAGER")).isPresent();
        assertThat(roleRepository.findByName("ADMIN")).isPresent();
    }

    @Test
    @DisplayName("ادمین همه مجوزها را دارد")
    void adminHasAllPermissions() {
        long totalPermissions = permissionRepository.count();

        var adminRole = roleRepository.findByName("ADMIN").orElseThrow();

        assertThat(adminRole.getPermissions()).hasSize((int) totalPermissions);
    }

    @Test
    @DisplayName("مشتری مجوز خرید دارد ولی فروشنده ندارد")
    void onlyCustomerCanBuy() {
        var userRole = roleRepository.findByName("USER").orElseThrow();
        var sellerRole = roleRepository.findByName("SELLER").orElseThrow();

        assertThat(permissionNames(userRole)).contains("CART_MANAGE", "ORDER_CREATE");
        assertThat(permissionNames(sellerRole)).doesNotContain("CART_MANAGE", "ORDER_CREATE");
    }

    @Test
    @DisplayName("مسیر عمومی محصولات بدون توکن در دسترس است")
    void publicEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
               .andExpect(status().isOk());
    }

    @Test
    @DisplayName("مسیر محافظت‌شده بدون توکن ۴۰۱ می‌دهد")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
               .andExpect(status().isUnauthorized());
    }

    private java.util.Set<String> permissionNames(com.shop.online_shop.entity.Role role) {
        return role.getPermissions().stream()
                .map(com.shop.online_shop.entity.Permission::getName)
                .collect(java.util.stream.Collectors.toSet());
    }
}