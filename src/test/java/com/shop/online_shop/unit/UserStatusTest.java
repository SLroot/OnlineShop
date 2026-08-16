package com.shop.online_shop.unit;

import com.shop.online_shop.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("وضعیت حساب کاربری")
class UserStatusTest {

    @Test
    @DisplayName("فقط حساب فعال می‌تواند وارد شود")
    void onlyActiveCanLogin() {
        assertThat(UserStatus.ACTIVE.canLogin()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class,
                names = {"PENDING", "REJECTED", "SUSPENDED"})
    @DisplayName("سایر وضعیت‌ها امکان ورود ندارند")
    void othersCannotLogin(UserStatus status) {
        assertThat(status.canLogin()).isFalse();
    }
}