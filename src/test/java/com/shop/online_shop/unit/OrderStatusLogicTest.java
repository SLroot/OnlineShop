package com.shop.online_shop.unit;

import com.shop.online_shop.entity.OrderItemStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("منطق وضعیت اقلام سفارش")
class OrderStatusLogicTest {

    @Nested
    @DisplayName("ترتیب پیشرفت")
    class Progress {

        @Test
        @DisplayName("چرخه وضعیت ترتیب صعودی دارد")
        void progressIsOrdered() {
            assertThat(OrderItemStatus.PENDING_PAYMENT.getProgress())
                    .isLessThan(OrderItemStatus.PAID.getProgress());

            assertThat(OrderItemStatus.PAID.getProgress())
                    .isLessThan(OrderItemStatus.PROCESSING.getProgress());

            assertThat(OrderItemStatus.PROCESSING.getProgress())
                    .isLessThan(OrderItemStatus.SHIPPED.getProgress());

            assertThat(OrderItemStatus.SHIPPED.getProgress())
                    .isLessThan(OrderItemStatus.DELIVERED.getProgress());
        }

        @Test
        @DisplayName("وضعیت لغو خارج از چرخه است")
        void cancelledIsOutsideCycle() {
            assertThat(OrderItemStatus.CANCELLED.getProgress()).isNegative();
        }
    }

    @Nested
    @DisplayName("امکان لغو توسط مشتری")
    class CustomerCancellation {

        @ParameterizedTest
        @EnumSource(value = OrderItemStatus.class,
                    names = {"PENDING_PAYMENT", "PAID", "PROCESSING"})
        @DisplayName("قبل از ارسال قابل لغو است")
        void cancellableBeforeShipping(OrderItemStatus status) {
            assertThat(status.isCancellableByCustomer()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OrderItemStatus.class,
                    names = {"SHIPPED", "DELIVERED", "CANCELLED"})
        @DisplayName("پس از ارسال قابل لغو نیست")
        void notCancellableAfterShipping(OrderItemStatus status) {
            assertThat(status.isCancellableByCustomer()).isFalse();
        }
    }

    @Nested
    @DisplayName("اقلام فعال")
    class ActiveItems {

        @ParameterizedTest
        @EnumSource(value = OrderItemStatus.class, names = "CANCELLED", mode =
                    EnumSource.Mode.EXCLUDE)
        @DisplayName("همه وضعیت‌ها جز لغو، فعال محسوب می‌شوند")
        void allButCancelledAreActive(OrderItemStatus status) {
            assertThat(status.isActive()).isTrue();
        }

        @Test
        @DisplayName("قلم لغوشده فعال نیست")
        void cancelledIsNotActive() {
            assertThat(OrderItemStatus.CANCELLED.isActive()).isFalse();
        }
    }
}