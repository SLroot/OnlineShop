package com.shop.online_shop;

import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("احراز هویت")
class AuthenticationTest extends BaseIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String ME = "/api/v1/auth/me";

    @Nested
    @DisplayName("ثبت‌نام")
    class Registration {

        @Test
        @DisplayName("مشتری جدید با نقش USER و وضعیت فعال ساخته می‌شود")
        void registersCustomer() throws Exception {
            String body = json(Map.of(
                    "email", "newbie@test.local",
                    "password", "Secret@123",
                    "fullName", "کاربر جدید",
                    "phone", "09121234567"));

            mockMvc.perform(post(REGISTER)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.role").value("USER"))
                    .andExpect(jsonPath("$.user.status").value("ACTIVE"));
        }

        @Test
        @DisplayName("رمز در دیتابیس هش می‌شود")
        void hashesPassword() throws Exception {
            String rawPassword = "Secret@123";

            mockMvc.perform(post(REGISTER)
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", "hashed@test.local",
                                    "password", rawPassword,
                                    "fullName", "کاربر",
                                    "phone", "09121234567"))))
                    .andExpect(status().isCreated());

            User saved = userRepository.findByEmail("hashed@test.local").orElseThrow();

            assertThat(saved.getPassword()).isNotEqualTo(rawPassword);
            assertThat(passwordEncoder.matches(rawPassword, saved.getPassword())).isTrue();
        }

        @Test
        @DisplayName("ایمیل تکراری رد می‌شود")
        void rejectsDuplicateEmail() throws Exception {
            String body = json(Map.of(
                    "email", customer.getEmail(),
                    "password", "Secret@123",
                    "fullName", "تکراری",
                    "phone", "09121234567"));

            mockMvc.perform(post(REGISTER)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("رمز کوتاه با خطای فیلد رد می‌شود")
        void rejectsShortPassword() throws Exception {
            String body = json(Map.of(
                    "email", "short@test.local",
                    "password", "123",
                    "fullName", "کاربر",
                    "phone", "09121234567"));

            mockMvc.perform(post(REGISTER)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.password").isNotEmpty());
        }

        @Test
        @DisplayName("ایمیل بدفرمت رد می‌شود")
        void rejectsInvalidEmail() throws Exception {
            String body = json(Map.of(
                    "email", "not-an-email",
                    "password", "Secret@123",
                    "fullName", "کاربر",
                    "phone", "09121234567"));

            mockMvc.perform(post(REGISTER)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details.email").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("ورود")
    class Login {

        @Test
        @DisplayName("اطلاعات درست، توکن برمی‌گرداند")
        void succeedsWithValidCredentials() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", customer.getEmail(),
                                    "password", DEFAULT_PASSWORD))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.user.role").value("USER"));
        }

        @Test
        @DisplayName("رمز اشتباه ۴۰۱ می‌دهد")
        void failsWithWrongPassword() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", customer.getEmail(),
                                    "password", "WrongPassword"))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ایمیل ناموجود همان ۴۰۱ می‌دهد — نه ۴۰۴")
        void failsWithUnknownEmail() throws Exception {
            mockMvc.perform(post(LOGIN)
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", "ghost@test.local",
                                    "password", DEFAULT_PASSWORD))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("فروشنده در انتظار تأیید نمی‌تواند وارد شود")
        void blocksPendingSeller() throws Exception {
            User pending = createUser("pending@test.local", "در انتظار",
                    "SELLER", UserStatus.PENDING);

            mockMvc.perform(post(LOGIN)
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", pending.getEmail(),
                                    "password", DEFAULT_PASSWORD))))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("حساب تعلیق‌شده نمی‌تواند وارد شود")
        void blocksSuspendedUser() throws Exception {
            User suspended = createUser("suspended@test.local", "تعلیق",
                    "SELLER", UserStatus.SUSPENDED);

            mockMvc.perform(post(LOGIN)
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "email", suspended.getEmail(),
                                    "password", DEFAULT_PASSWORD))))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("توکن")
    class Tokens {

        @Test
        @DisplayName("با توکن معتبر اطلاعات کاربر برمی‌گردد")
        void returnsCurrentUser() throws Exception {
            mockMvc.perform(get(ME).header("Authorization", bearerFor(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(customer.getEmail()))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.permissions").isArray());
        }

        @Test
        @DisplayName("پاسخ /me رمز عبور را افشا نمی‌کند")
        void neverExposesPassword() throws Exception {
            mockMvc.perform(get(ME).header("Authorization", bearerFor(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("توکن دستکاری‌شده ۴۰۱ می‌دهد")
        void rejectsTamperedToken() throws Exception {
            String tampered = bearerFor(customer) + "xyz";

            mockMvc.perform(get(ME).header("Authorization", tampered))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("هدر بدون پیشوند Bearer نادیده گرفته می‌شود")
        void rejectsMalformedHeader() throws Exception {
            mockMvc.perform(get(ME).header("Authorization", "SomeRandomValue"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("خطای ۴۰۱ ساختار استاندارد پاسخ را دارد")
        void unauthorizedFollowsErrorSchema() throws Exception {
            mockMvc.perform(get(ME))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("پروفایل")
    class Profile {

        @Test
        @DisplayName("تغییر رمز با رمز فعلی درست انجام می‌شود")
        void changesPassword() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/me/password")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "currentPassword", DEFAULT_PASSWORD,
                                    "newPassword", "BrandNew@456"))))
                    .andExpect(status().isNoContent());

            User updated = userRepository.findById(customer.getId()).orElseThrow();
            assertThat(passwordEncoder.matches("BrandNew@456", updated.getPassword())).isTrue();
        }

        @Test
        @DisplayName("رمز فعلی اشتباه، تغییر را رد می‌کند")
        void rejectsWrongCurrentPassword() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/me/password")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "currentPassword", "Wrong@000",
                                    "newPassword", "BrandNew@456"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("رمز جدید نباید با رمز فعلی یکسان باشد")
        void rejectsSamePassword() throws Exception {
            mockMvc.perform(patch("/api/v1/auth/me/password")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "currentPassword", DEFAULT_PASSWORD,
                                    "newPassword", DEFAULT_PASSWORD))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("ویرایش پروفایل نام را به‌روز می‌کند")
        void updatesProfile() throws Exception {
            mockMvc.perform(patch(ME)
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "fullName", "نام جدید",
                                    "phone", "09129999999"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fullName").value("نام جدید"));
        }
    }
}