package com.shop.online_shop;

import com.shop.online_shop.entity.Category;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.repository.CategoryRepository;
import com.shop.online_shop.repository.ProductRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("کنترل دسترسی")
class AuthorizationTest extends BaseIntegrationTest {

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    private static final String PRODUCTS = "/api/v1/products";
    private static final String CATEGORIES = "/api/v1/categories";
    private static final String ADDRESSES = "/api/v1/addresses";
    private static final String ROLES = "/api/v1/roles";

    private Category leaf;
    private Product sellerProduct;
    private User otherSeller;

    @BeforeEach
    void setUpCatalog() {
        Category root = categoryRepository.save(Category.builder()
                .name("کالای دیجیتال").slug("digital").depth(1).build());

        leaf = categoryRepository.save(Category.builder()
                .name("لپ‌تاپ").slug("laptop").parent(root).depth(2).build());

        sellerProduct = productRepository.save(Product.builder()
                .sku("SKU-001")
                .name("لپ‌تاپ ایسوس")
                .price(new BigDecimal("50000000"))
                .stock(10)
                .category(leaf)
                .seller(seller)
                .active(true)
                .build());

        otherSeller = createUser("seller2@test.local", "فروشنده دوم",
                RoleCode.SELLER, UserStatus.ACTIVE);
    }

    // ==================== بدون احراز هویت ====================

    @Nested
    @DisplayName("بدون احراز هویت")
    class Anonymous {

        @Test
        @DisplayName("مشاهده کاتالوگ آزاد است")
        void canBrowseCatalog() throws Exception {
            mockMvc.perform(get(PRODUCTS)).andExpect(status().isOk());
            mockMvc.perform(get(CATEGORIES)).andExpect(status().isOk());
        }

        @Test
        @DisplayName("ساخت محصول ۴۰۱ می‌دهد")
        void cannotCreateProduct() throws Exception {
            mockMvc.perform(post(PRODUCTS)
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
        @DisplayName("مدیریت نقش‌ها ۴۰۱ می‌دهد")
        void cannotAccessRoles() throws Exception {
            mockMvc.perform(get(ROLES))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("خطای ۴۰۱ ساختار استاندارد پاسخ را دارد")
        void unauthorizedFollowsErrorSchema() throws Exception {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    // ==================== دامنه دید ====================

    @Nested
    @DisplayName("دامنه دید محصولات")
    class ProductScope {

        @Test
        @DisplayName("دامنه عمومی برای همه باز است")
        void publicScopeIsOpen() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=public"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("فروشنده محصولات خودش را می‌بیند")
        void sellerCanSeeOwnProducts() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=mine")
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].seller.id").value(seller.getId()));
        }

        @Test
        @DisplayName("مشتری بدون مجوز نمی‌تواند دامنه mine را ببیند")
        void customerCannotUseMineScope() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=mine")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error")
                            .value(Matchers.containsString("PRODUCT_READ_OWN")));
        }

        @Test
        @DisplayName("مدیر می‌تواند همه محصولات را ببیند")
        void managerCanUseAllScope() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=all")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند دامنه all را ببیند")
        void sellerCannotUseAllScope() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=all")
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error")
                            .value(Matchers.containsString("PRODUCT_MANAGE_ALL")));
        }

        @Test
        @DisplayName("دامنه نامعتبر ۴۰۰ می‌دهد")
        void invalidScopeIsRejected() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=everything")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("مدیر می‌تواند محصولات یک فروشنده را فیلتر کند")
        void managerCanFilterBySeller() throws Exception {
            mockMvc.perform(get(PRODUCTS + "?scope=all&sellerId=" + seller.getId())
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].seller.id").value(seller.getId()));
        }

        @Test
        @DisplayName("محصول غیرفعال تنها برای مالک و مدیر دیده می‌شود")
        void inactiveProductVisibilityDependsOnViewer() throws Exception {
            mockMvc.perform(delete(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(PRODUCTS + "/" + sellerProduct.getId()))
                    .andExpect(status().isNotFound());

            mockMvc.perform(get(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isOk());

            mockMvc.perform(get(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk());
        }
    }

    // ==================== مجوز ناکافی ====================

    @Nested
    @DisplayName("مجوز ناکافی")
    class InsufficientPermission {

        @Test
        @DisplayName("مشتری نمی‌تواند محصول بسازد")
        void customerCannotCreateProduct() throws Exception {
            mockMvc.perform(post(PRODUCTS)
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(productBody("SKU-NEW")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مشتری نمی‌تواند دسته‌بندی بسازد")
        void customerCannotCreateCategory() throws Exception {
            mockMvc.perform(post(CATEGORIES)
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("name", "دسته جدید"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مدیر می‌تواند دسته‌بندی بسازد")
        void managerCanCreateCategory() throws Exception {
            mockMvc.perform(post(CATEGORIES)
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(json(Map.of("name", "دسته تازه"))))
                    .andExpect(status().isCreated());
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
        @DisplayName("مدیر بدون ROLE_MANAGE نمی‌تواند نقش بسازد")
        void managerCannotCreateRole() throws Exception {
            mockMvc.perform(post(ROLES)
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "name", "نقش جدید",
                                    "permissionIds", List.of(),
                                    "requiresSellerApproval", false))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مشتری نمی‌تواند فروشندگان را ببیند")
        void customerCannotListSellers() throws Exception {
            mockMvc.perform(get("/api/v1/sellers")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("خطای ۴۰۳ ساختار استاندارد پاسخ را دارد")
        void forbiddenFollowsErrorSchema() throws Exception {
            mockMvc.perform(get("/api/v1/sellers")
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
            mockMvc.perform(put(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(otherSeller))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند محصول دیگری را غیرفعال کند")
        void sellerCannotDeactivateOthersProduct() throws Exception {
            mockMvc.perform(delete(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(otherSeller)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("فروشنده محصول خودش را می‌تواند ویرایش کند")
        void sellerCanEditOwnProduct() throws Exception {
            mockMvc.perform(put(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("مدیر می‌تواند محصول هر فروشنده‌ای را ویرایش کند")
        void managerCanEditAnyProduct() throws Exception {
            mockMvc.perform(put(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("مالک محصول با ویرایش توسط مدیر تغییر نمی‌کند")
        void ownershipSurvivesManagerEdit() throws Exception {
            mockMvc.perform(put(PRODUCTS + "/" + sellerProduct.getId())
                            .header("Authorization", bearerFor(manager))
                            .contentType("application/json")
                            .content(productBody("SKU-001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seller.id").value(seller.getId()));
        }

        @Test
        @DisplayName("آدرس کاربر دیگر ۴۰۴ می‌دهد نه ۴۰۳")
        void othersAddressReturnsNotFound() throws Exception {
            String created = mockMvc.perform(post(ADDRESSES)
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(addressBody()))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long addressId = objectMapper.readTree(created).get("id").asLong();

            User other = createUser("customer2@test.local", "مشتری دوم",
                    RoleCode.USER, UserStatus.ACTIVE);

            mockMvc.perform(get(ADDRESSES + "/" + addressId)
                            .header("Authorization", bearerFor(other)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("مدیر می‌تواند آدرس همه کاربران را ببیند")
        void managerSeesAllAddresses() throws Exception {
            mockMvc.perform(post(ADDRESSES)
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(addressBody()))
                    .andExpect(status().isCreated());

            mockMvc.perform(get(ADDRESSES + "/all")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].owner").isNotEmpty());
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
                "categoryId", leaf.getId()));
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