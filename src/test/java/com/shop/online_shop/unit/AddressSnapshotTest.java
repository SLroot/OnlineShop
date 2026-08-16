package com.shop.online_shop.unit;

import com.shop.online_shop.entity.Address;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("تبدیل آدرس به متن")
class AddressSnapshotTest {

    @Test
    @DisplayName("متن آدرس شامل همه اجزاست")
    void snapshotContainsAllParts() {
        Address address = Address.builder()
                .title("خانه")
                .province("تهران")
                .city("کرج")
                .fullAddress("خیابان آزادی، پلاک ۱۲")
                .postalCode("1234567890")
                .build();

        String snapshot = address.toSnapshot();

        assertThat(snapshot)
                .contains("تهران")
                .contains("کرج")
                .contains("خیابان آزادی، پلاک ۱۲")
                .contains("1234567890");
    }

    @Test
    @DisplayName("عنوان آدرس در متن نمی‌آید")
    void snapshotOmitsTitle() {
        Address address = Address.builder()
                .title("محل کار")
                .province("تهران")
                .city("تهران")
                .fullAddress("خیابان ولیعصر")
                .postalCode("1111111111")
                .build();

        assertThat(address.toSnapshot()).doesNotContain("محل کار");
    }
}