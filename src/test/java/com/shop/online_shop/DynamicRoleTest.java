package com.shop.online_shop;

import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("نقش پویا و کنترل دسترسی مبتنی بر مجوز")
class DynamicRoleTest extends BaseIntegrationTest {

    private static final String ROLES = "/api/v1/roles";
    private static final String USERS = "/api/v1/users";

    // ==================== ساخت و ویرایش نقش ====================

    @Nested
    @DisplayName("مدیریت نقش")
    class RoleManagement {

        @Test
        @DisplayName("ادمین می‌تواند نقش سفارشی بسازد")
        void adminCreatesCustomRole() throws Exception {
            String body = json(Map.of(
                    "name", "حسابدار",
                    "description", "مشاهده سفارش‌ها و پرداخت‌ها",
                    "permissionIds", List.of(
                            permissionId("ORDER_READ"),
                            permissionId("ORDER_READ_ALL")),
                    "requiresSellerApproval", false));

            mockMvc.perform(post(ROLES)
                            .header("Authorization", bearerFor(admin))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").doesNotExist())
                    .andExpect(jsonPath("$.custom").value(true))
                    .andExpect(jsonPath("$.systemRole").value(false))
                    .andExpect(jsonPath("$.permissions.length()").value(2));
        }

        @Test
        @DisplayName("نام نقش تکراری رد می‌شود")
        void rejectsDuplicateRoleName() throws Exception {
            Role existing = roleRepository.findByCode(RoleCode.USER).orElseThrow();

            String body = json(Map.of(
                    "name", existing.getName(),
                    "permissionIds", List.of(),
                    "requiresSellerApproval", false));

            mockMvc.perform(post(ROLES)
                            .header("Authorization", bearerFor(admin))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("نقش پایه قابل حذف نیست")
        void systemRoleCannotBeDeleted() throws Exception {
            mockMvc.perform(delete(ROLES + "/" + roleId(RoleCode.USER))
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("نقشی که کاربر دارد قابل حذف نیست")
        void roleWithUsersCannotBeDeleted() throws Exception {
            Role custom = createCustomRole("نقش پرکاربر", "PRODUCT_READ");
            createUserWithRole("holder@test.local", "دارنده نقش", custom);

            mockMvc.perform(delete(ROLES + "/" + custom.getId())
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("نقش سفارشی بدون کاربر حذف می‌شود")
        void unusedCustomRoleIsDeleted() throws Exception {
            Role custom = createCustomRole("نقش موقت", "PRODUCT_READ");

            mockMvc.perform(delete(ROLES + "/" + custom.getId())
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("مجوز ROLE_MANAGE از نقش ادمین برداشته نمی‌شود")
        void adminKeepsRoleManage() throws Exception {
            mockMvc.perform(delete(ROLES + "/" + roleId(RoleCode.ADMIN)
                            + "/permissions/" + permissionId("ROLE_MANAGE"))
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("مجوزها گروه‌بندی‌شده بر اساس منبع برمی‌گردند")
        void permissionsAreGrouped() throws Exception {
            mockMvc.perform(get(ROLES + "/permissions/grouped")
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].resource").isNotEmpty())
                    .andExpect(jsonPath("$[0].permissions").isArray());
        }
    }

    // ==================== قاعده ارتقای دسترسی ====================

    @Nested
    @DisplayName("جلوگیری از ارتقای دسترسی")
    class PrivilegeEscalation {

        @Test
        @DisplayName("نمی‌توان مجوزی داد که خود فرد ندارد")
        void cannotGrantPermissionYouLack() throws Exception {
            // نقشی با اجازه مدیریت نقش‌ها، ولی بدون USER_MANAGE
            Role limited = createCustomRole("مدیر نقش‌ها", "ROLE_READ", "ROLE_MANAGE");
            User holder = createUserWithRole("limited@test.local", "مدیر محدود", limited);

            String body = json(Map.of(
                    "name", "نقش پرقدرت",
                    "permissionIds", List.of(permissionId("USER_MANAGE")),
                    "requiresSellerApproval", false));

            mockMvc.perform(post(ROLES)
                            .header("Authorization", bearerFor(holder))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value(
                            org.hamcrest.Matchers.containsString("USER_MANAGE")));
        }

        @Test
        @DisplayName("مجوزی که خود فرد دارد قابل اعطاست")
        void canGrantPermissionYouHave() throws Exception {
            Role limited = createCustomRole("مدیر نقش‌ها",
                    "ROLE_READ", "ROLE_MANAGE", "PRODUCT_READ");
            User holder = createUserWithRole("limited2@test.local", "مدیر محدود", limited);

            String body = json(Map.of(
                    "name", "نقش خواندنی",
                    "permissionIds", List.of(permissionId("PRODUCT_READ")),
                    "requiresSellerApproval", false));

            mockMvc.perform(post(ROLES)
                            .header("Authorization", bearerFor(holder))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("نمی‌توان نقشی فراتر از دسترسی خود به کاربری داد")
        void cannotAssignRoleBeyondOwnAccess() throws Exception {
            Role limited = createCustomRole("سازنده کاربر",
                    "USER_READ", "USER_CREATE", "PRODUCT_READ");
            User holder = createUserWithRole("creator@test.local", "سازنده", limited);

            String body = json(Map.of(
                    "email", "newadmin@test.local",
                    "initialPassword", "Initial@123",
                    "fullName", "کاربر تازه",
                    "phone", "09121111111",
                    "roleId", roleId(RoleCode.ADMIN)));

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearerFor(holder))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== رفتار نقش سفارشی ====================

    @Nested
    @DisplayName("دسترسی نقش سفارشی")
    class CustomRoleBehaviour {

        @Test
        @DisplayName("نقش حسابدار تنها به سفارش و پرداخت دسترسی دارد")
        void accountantSeesOnlyFinancialData() throws Exception {
            Role accountant = createCustomRole("حسابدار",
                    "ORDER_READ", "ORDER_READ_ALL", "PAYMENT_READ", "PAYMENT_READ_ALL");

            User user = createUserWithRole("accountant@test.local", "حسابدار", accountant);
            String token = bearerFor(user);

            mockMvc.perform(get("/api/v1/orders?scope=all").header("Authorization", token))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/payments").header("Authorization", token))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/products?scope=all").header("Authorization", token))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/users").header("Authorization", token))
                    .andExpect(status().isForbidden());

            mockMvc.perform(get("/api/v1/sellers").header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("مجوزهای نقش سفارشی در پاسخ /me می‌آید")
        void customRolePermissionsAppearInMe() throws Exception {
            Role custom = createCustomRole("ناظر", "PRODUCT_READ", "ORDER_READ");
            User user = createUserWithRole("viewer@test.local", "ناظر", custom);

            mockMvc.perform(get("/api/v1/auth/me")
                            .header("Authorization", bearerFor(user)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.role").value("ناظر"))
                    .andExpect(jsonPath("$.permissions.length()").value(2));
        }

        @Test
        @DisplayName("نقش سفارشی فروشنده‌محور وارد جریان تأیید می‌شود")
        void customSellerRoleJoinsApprovalFlow() {
            Role goldSeller = roleRepository.save(Role.builder()
                    .code(null)
                    .name("فروشنده طلایی")
                    .permissions(roleRepository.findByCode(RoleCode.SELLER)
                            .orElseThrow().getPermissions())
                    .systemRole(false)
                    .requiresSellerApproval(true)
                    .openRegistration(false)
                    .build());

            User user = createUserWithRole("gold@test.local", "فروشنده طلایی", goldSeller);

            // شناسایی بر اساس پرچم نقش انجام می‌شود، نه نام آن
            assertThat(user.needsSellerApproval()).isTrue();
        }
    }

    // ==================== ابطال نشست ====================

    @Nested
    @DisplayName("انتشار تغییر دسترسی")
    class SessionInvalidation {

        @Test
        @DisplayName("تغییر مجوز نقش، نشست دارندگانش را باطل می‌کند")
        void permissionChangeRevokesSessions() throws Exception {
            Role custom = createCustomRole("ناظر موقت", "PRODUCT_READ");
            User user = createUserWithRole("temp@test.local", "ناظر", custom);

            String refreshToken = loginAndGetRefreshToken(user.getEmail());

            mockMvc.perform(post(ROLES + "/" + custom.getId()
                            + "/permissions/" + permissionId("ORDER_READ"))
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType("application/json")
                            .content(json(Map.of("refreshToken", refreshToken))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("تغییر نقش کاربر، نشست او را باطل می‌کند")
        void roleChangeRevokesSessions() throws Exception {
            Role custom = createCustomRole("نقش تازه", "PRODUCT_READ");
            String refreshToken = loginAndGetRefreshToken(customer.getEmail());

            mockMvc.perform(patch(USERS + "/" + customer.getId() + "/role")
                            .header("Authorization", bearerFor(admin))
                            .contentType("application/json")
                            .content(json(Map.of("roleId", custom.getId()))))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType("application/json")
                            .content(json(Map.of("refreshToken", refreshToken))))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================== محافظت از ادمین ====================

    @Nested
    @DisplayName("محافظت از حساب‌ها")
    class AccountProtection {

        @Test
        @DisplayName("کاربر نمی‌تواند نقش خودش را عوض کند")
        void cannotChangeOwnRole() throws Exception {
            mockMvc.perform(patch(USERS + "/" + admin.getId() + "/role")
                            .header("Authorization", bearerFor(admin))
                            .contentType("application/json")
                            .content(json(Map.of("roleId", roleId(RoleCode.USER)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("کاربر نمی‌تواند حساب خودش را تعلیق کند")
        void cannotSuspendSelf() throws Exception {
            mockMvc.perform(patch(USERS + "/" + admin.getId() + "/suspend")
                            .header("Authorization", bearerFor(admin)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ساخت کاربر با رمز اولیه، الزام تغییر رمز می‌گذارد")
        void createdUserMustChangePassword() throws Exception {
            String body = json(Map.of(
                    "email", "fresh@test.local",
                    "initialPassword", "Initial@123",
                    "fullName", "کاربر تازه",
                    "phone", "09121111111",
                    "roleId", roleId(RoleCode.MANAGER)));

            mockMvc.perform(post(USERS)
                            .header("Authorization", bearerFor(admin))
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.mustChangePassword").value(true));
        }
    }

    // ==================== کمکی ====================

    private String loginAndGetRefreshToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(json(Map.of(
                                "email", email,
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("refreshToken").asText();
    }
}