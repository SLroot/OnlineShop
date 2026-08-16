package com.shop.online_shop;

import com.shop.online_shop.entity.Category;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.repository.CategoryRepository;
import com.shop.online_shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("کنترل دسترسی")
class AuthorizationTest extends BaseIntegrationTest {

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    private Category leafCategory;
    private Product sellerProduct;
    private User otherSeller;

    @BeforeEach
    void setUpCatalog() {
        Category root = categoryRepository.save(Category.builder()
                .name("کالای دیجیتال").slug("digital").depth(1).build());

        leafCategory = categoryRepository.save(Category.builder()
                .name("لپ‌تاپ").slug("laptop").parent(root).depth(2).build());

        sellerProduct = productRepository.save(Product.builder()
                .sku("SKU-001")
                .name("لپ‌تاپ ایسوس")
                .price(new BigDecimal("50000000"))
                .stock(10)
                .category(leafCategory)
                .seller(seller)
                .active(true)
                .build());

        otherSeller = createUser("seller2@test.local", "فروشنده دوم",
                "SELLER", com.shop.online_shop.entity.UserStatus.ACTIVE);
    }

    // ==================== بدون توکن ====================

    @Nested
    @DisplayName("بدون احراز هویت")
    class Anonymous {

        @Test
        @DisplayName("مشاهده محصولات آزاد است")
        void canBrowseProducts() throws Exception {
            mockMvc.perform(get("/api/v1/products"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("مشاهده دسته‌بندی‌ها آزاد است")
        void canBrowseCategories() throws Exception {
            mockMvc.perform(get("/api/v1/categories"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ساخت محصول ۴۰۱ می‌دهد")
        void cannotCreateProduct() throws Exception {
            mockMvc.perform(post("/api/v1/seller/products")
                            .contentType("application/json")
                            .content(productBody("SKU-NEW")))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("مشاهده سبد ۴۰۱ می‌دهد")
        void cannotViewCart() throws Exception {
            mockMvc.perform(get("/api/v1/carts/me"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("پنل ادمین ۴۰۱ می‌دهد")
        void cannotAccessAdminPanel() throws Exception {
            mockMvc.perform(get("/api/v1/admin/roles"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== نقش اشتباه ====================

    @Nested
    @DisplayName("مجوز ناکافی")
    class InsufficientPermission {

        @Test
        @DisplayName("مشتری نمی‌تواند محصول بسازد")
        void customerCannotCreateProduct() throws Exception {
            mockMvc.perform(post("/api/v1/seller/products")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(productBody("SKU-NEW")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مشتری نمی‌تواند دسته‌بندی بسازد")
        void customerCannotCreateCategory() throws Exception {
            mockMvc.perform(post("/api/v1/admin/categories")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("name", "دسته جدید"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مشتری نمی‌تواند فروشندگان را ببیند")
        void customerCannotListSellers() throws Exception {
            mockMvc.perform(get("/api/v1/admin/sellers")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند خرید کند")
        void sellerCannotUseCart() throws Exception {
            mockMvc.perform(get("/api/v1/carts/me")
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مدیر نمی‌تواند خرید کند")
        void managerCannotUseCart() throws Exception {
            mockMvc.perform(get("/api/v1/carts/me")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مدیر نمی‌تواند مدیر جدید بسازد — فقط ادمین")
        void managerCannotCreateManager() throws Exception {
            mockMvc.perform(post("/api/v1/admin/managers")
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", "newmanager@test.local",
                                    "initialPassword", "Initial@123",
                                    "fullName", "مدیر جدید",
                                    "phone", "09121234567"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("خطای ۴۰۳ ساختار استاندارد پاسخ را دارد")
        void forbiddenFollowsErrorSchema() throws Exception {
            mockMvc.perform(get("/api/v1/admin/sellers")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    // ==================== مالکیت رکورد ====================

    @Nested
    @DisplayName("مالکیت رکورد")
    class RecordOwnership {

        @Test
        @DisplayName("فروشنده نمی‌تواند محصول فروشنده دیگر را ویرایش کند")
        void sellerCannotEditOthersProduct() throws Exception {
            mockMvc.perform(put("/api/v1/seller/products/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(otherSeller))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند محصول فروشنده دیگر را غیرفعال کند")
        void sellerCannotDeactivateOthersProduct() throws Exception {
            mockMvc.perform(delete("/api/v1/seller/products/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(otherSeller)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("فروشنده محصول خودش را می‌تواند ویرایش کند")
        void sellerCanEditOwnProduct() throws Exception {
            mockMvc.perform(put("/api/v1/seller/products/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("مدیر می‌تواند محصول هر فروشنده‌ای را ویرایش کند")
        void managerCanEditAnyProduct() throws Exception {
            mockMvc.perform(put("/api/v1/seller/products/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("مالک محصول با ویرایش توسط مدیر تغییر نمی‌کند")
        void ownershipSurvivesManagerEdit() throws Exception {
            mockMvc.perform(put("/api/v1/seller/products/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seller.id").value(seller.getId()));
        }

        @Test
        @DisplayName("آدرس کاربر دیگر ۴۰۴ می‌دهد — نه ۴۰۳")
        void othersAddressReturnsNotFound() throws Exception {
            String created = mockMvc.perform(post("/api/v1/addresses")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(addressBody()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long addressId = objectMapper.readTree(created).get("id").asLong();

            User otherCustomer = createUser("customer2@test.local", "مشتری دوم",
                    "USER", com.shop.online_shop.entity.UserStatus.ACTIVE);

            mockMvc.perform(get("/api/v1/addresses/" + addressId)
                            .header("Authorization", bearerFor(otherCustomer)))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== قواعد پنل ادمین ====================

    @Nested
    @DisplayName("قواعد ویرایش نقش")
    class RoleEditing {

        @Test
        @DisplayName("مجوزهای نقش ADMIN قابل تغییر نیست")
        void adminRoleIsLocked() throws Exception {
            Long adminRoleId = roleRepository.findByName("ADMIN").orElseThrow().getId();
            Long anyPermissionId = roleRepository.findByName("USER").orElseThrow()
                    .getPermissions().iterator().next().getId();

            mockMvc.perform(post("/api/v1/admin/roles/" + adminRoleId
                            + "/permissions/" + anyPermissionId)
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مجوزهای نقش USER قابل تغییر نیست")
        void userRoleIsLocked() throws Exception {
            Long userRoleId = roleRepository.findByName("USER").orElseThrow().getId();
            Long anyPermissionId = roleRepository.findByName("MANAGER").orElseThrow()
                    .getPermissions().iterator().next().getId();

            mockMvc.perform(post("/api/v1/admin/roles/" + userRoleId
                            + "/permissions/" + anyPermissionId)
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("ادمین لیست نقش‌ها را با پرچم قابل‌ویرایش می‌بیند")
        void adminSeesEditableFlag() throws Exception {
            mockMvc.perform(get("/api/v1/admin/roles")
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.name == 'ADMIN')].editable").value(false))
                    .andExpect(jsonPath("$[?(@.name == 'MANAGER')].editable").value(true));
        }
    }

    // ==================== کمکی ====================

    private String productBody(String sku) throws Exception {
        return json(Map.of(
                "sku", sku,
                "name", "محصول تست",
                "description", "توضیحات",
                "price", 1000000,
                "stock", 5,
                "categoryId", leafCategory.getId()));
    }

    private String addressBody() throws Exception {
        return json(Map.of(
                "title", "خانه",
                "province", "تهران",
                "city", "تهران",
                "fullAddress", "خیابان آزادی، پلاک ۱",
                "postalCode", "1234567890",
                "setAsDefault", true));
    }
}