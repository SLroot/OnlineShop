package com.shop.online_shop;

import com.shop.online_shop.entity.*;
import com.shop.online_shop.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("جریان سفارش")
class OrderFlowTest extends BaseIntegrationTest {

    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CartRepository cartRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired AddressRepository addressRepository;

    private static final int INITIAL_STOCK = 10;
    private static final BigDecimal PRICE = new BigDecimal("1000000");

    private Product product;
    private Address address;

    @BeforeEach
    void setUpShop() {
        Category root = categoryRepository.save(Category.builder()
                .name("کالای دیجیتال").slug("digital").depth(1).build());

        Category leaf = categoryRepository.save(Category.builder()
                .name("لپ‌تاپ").slug("laptop").parent(root).depth(2).build());

        product = productRepository.save(Product.builder()
                .sku("SKU-ORDER-1")
                .name("لپ‌تاپ تست")
                .price(PRICE)
                .stock(INITIAL_STOCK)
                .category(leaf)
                .seller(seller)
                .active(true)
                .build());

        cartRepository.save(Cart.builder().user(customer).build());

        address = addressRepository.save(Address.builder()
                .user(customer)
                .title("خانه")
                .province("تهران")
                .city("تهران")
                .fullAddress("خیابان آزادی")
                .postalCode("1234567890")
                .isDefault(true)
                .build());
    }

    // ==================== سبد خرید ====================

    @Nested
    @DisplayName("سبد خرید")
    class CartBehaviour {

        @Test
        @DisplayName("سبد خالی پیام مناسب دارد")
        void emptyCartHasMessage() throws Exception {
            mockMvc.perform(get("/api/v1/carts/me")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("افزودن دوباره همان محصول، تعداد را جمع می‌کند")
        void addingSameProductAccumulates() throws Exception {
            addToCart(product.getId(), 2);
            addToCart(product.getId(), 3);

            mockMvc.perform(get("/api/v1/carts/me")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.itemCount").value(1))
                    .andExpect(jsonPath("$.totalQuantity").value(5));
        }

        @Test
        @DisplayName("تعداد بیش از موجودی، به موجودی محدود می‌شود")
        void quantityIsCappedToStock() throws Exception {
            mockMvc.perform(post("/api/v1/carts/me/items")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of(
                                    "productId", product.getId(),
                                    "quantity", INITIAL_STOCK + 50))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalQuantity").value(INITIAL_STOCK))
                    .andExpect(jsonPath("$.notices").isNotEmpty());
        }

        @Test
        @DisplayName("محصول غیرفعال از سبد همه کاربران حذف می‌شود")
        void deactivatedProductLeavesCart() throws Exception {
            addToCart(product.getId(), 2);

            mockMvc.perform(delete("/api/v1/seller/products/" + product.getId())
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/carts/me")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("سبد، موجودی محصول را رزرو نمی‌کند")
        void cartDoesNotReserveStock() throws Exception {
            addToCart(product.getId(), 5);

            Product fresh = productRepository.findById(product.getId()).orElseThrow();
            assertThat(fresh.getStock()).isEqualTo(INITIAL_STOCK);
        }
    }

    // ==================== ثبت سفارش ====================

    @Nested
    @DisplayName("ثبت سفارش")
    class PlacingOrder {

        @Test
        @DisplayName("موجودی در لحظه ثبت سفارش کسر می‌شود")
        void stockIsDeducted() throws Exception {
            addToCart(product.getId(), 3);
            placeOrder();

            Product fresh = productRepository.findById(product.getId()).orElseThrow();
            assertThat(fresh.getStock()).isEqualTo(INITIAL_STOCK - 3);
        }

        @Test
        @DisplayName("سبد پس از ثبت سفارش خالی می‌شود")
        void cartIsEmptiedAfterOrder() throws Exception {
            addToCart(product.getId(), 2);
            placeOrder();

            mockMvc.perform(get("/api/v1/carts/me")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("سفارش با وضعیت در انتظار پرداخت و مهلت ساخته می‌شود")
        void orderStartsPendingPayment() throws Exception {
            addToCart(product.getId(), 1);

            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("addressId", address.getId()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.order.status").value("PENDING_PAYMENT"))
                    .andExpect(jsonPath("$.order.paymentDeadline").isNotEmpty());
        }

        @Test
        @DisplayName("آدرس به صورت متن در سفارش ذخیره می‌شود")
        void addressIsSnapshotted() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            Order order = orderRepository.findById(orderId).orElseThrow();

            assertThat(order.getShippingAddress())
                    .contains("تهران")
                    .contains("خیابان آزادی");
        }

        @Test
        @DisplayName("قیمت لحظه خرید در قلم سفارش ثابت می‌ماند")
        void unitPriceIsFrozen() throws Exception {
            addToCart(product.getId(), 2);
            Long orderId = placeOrder();

            // فروشنده بعداً قیمت را عوض می‌کند
            Product p = productRepository.findById(product.getId()).orElseThrow();
            p.setPrice(new BigDecimal("9999999"));
            productRepository.save(p);

            Order order = orderRepository.findById(orderId).orElseThrow();
            OrderItem item = order.getItems().get(0);

            assertThat(item.getUnitPrice()).isEqualByComparingTo(PRICE);
        }

        @Test
        @DisplayName("سبد خالی، سفارش نمی‌سازد")
        void emptyCartCannotOrder() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("addressId", address.getId()))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند سفارش ثبت کند")
        void sellerCannotPlaceOrder() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("addressId", address.getId()))))
                    .andExpect(status().isForbidden());
        }
    }

    // ==================== پرداخت ====================

    @Nested
    @DisplayName("پرداخت")
    class PaymentFlow {

        @Test
        @DisplayName("پرداخت موفق، سفارش را به وضعیت پرداخت‌شده می‌برد")
        void successfulPaymentUpdatesOrder() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/payment")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("simulateSuccess", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.transactionRef").isNotEmpty());

            mockMvc.perform(get("/api/v1/orders/" + orderId)
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.items[0].status").value("PAID"));
        }

        @Test
        @DisplayName("پرداخت ناموفق، سفارش را لغو نمی‌کند")
        void failedPaymentKeepsOrderOpen() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/payment")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("simulateSuccess", false))))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get("/api/v1/orders/" + orderId)
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("پرداخت دوباره رد می‌شود")
        void doublePaymentIsRejected() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();
            pay(orderId);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/payment")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("simulateSuccess", true))))
                    .andExpect(status().isBadRequest());
        }
    }

    // ==================== چرخه وضعیت ====================

    @Nested
    @DisplayName("چرخه وضعیت اقلام")
    class StatusTransitions {

        @Test
        @DisplayName("فروشنده وضعیت قلم خودش را جلو می‌برد")
        void sellerAdvancesStatus() throws Exception {
            Long itemId = paidOrderItem();

            mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/status")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("status", "PROCESSING"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("پرش از مراحل مجاز نیست")
        void cannotSkipStages() throws Exception {
            Long itemId = paidOrderItem();

            mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/status")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("status", "SHIPPED"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("بازگشت به وضعیت قبلی مجاز نیست")
        void cannotGoBackwards() throws Exception {
            Long itemId = paidOrderItem();
            advance(itemId, "PROCESSING");
            advance(itemId, "SHIPPED");

            mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/status")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("status", "PROCESSING"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("تا پرداخت انجام نشود وضعیت قابل تغییر نیست")
        void cannotAdvanceBeforePayment() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();
            Long itemId = firstItemId(orderId);

            mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/status")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("status", "PROCESSING"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("فروشنده به قلم فروشنده دیگر دسترسی ندارد")
        void sellerCannotTouchOthersItem() throws Exception {
            Long itemId = paidOrderItem();

            User otherSeller = createUser("seller9@test.local", "فروشنده دیگر",
                    "SELLER", UserStatus.ACTIVE);

            mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/status")
                            .header("Authorization", bearerFor(otherSeller))
                            .contentType("application/json")
                            .content(json(Map.of("status", "PROCESSING"))))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================== لغو ====================

    @Nested
    @DisplayName("لغو سفارش")
    class Cancellation {

        @Test
        @DisplayName("لغو سفارش، موجودی را برمی‌گرداند")
        void cancellationRestoresStock() throws Exception {
            addToCart(product.getId(), 4);
            Long orderId = placeOrder();

            Product afterOrder = productRepository.findById(product.getId()).orElseThrow();
            assertThat(afterOrder.getStock()).isEqualTo(INITIAL_STOCK - 4);

            cancel(orderId);

            Product afterCancel = productRepository.findById(product.getId()).orElseThrow();
            assertThat(afterCancel.getStock()).isEqualTo(INITIAL_STOCK);
        }

        @Test
        @DisplayName("لغو سفارش پرداخت‌شده، بازپرداخت ثبت می‌کند")
        void cancellationRefundsPayment() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();
            pay(orderId);

            cancel(orderId);

            Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();
            assertThat(payment.getStatus()).isEqualTo(Payment.PaymentStatus.REFUNDED);
            assertThat(payment.getRefundedAt()).isNotNull();
        }

        @Test
        @DisplayName("سفارش ارسال‌شده قابل لغو نیست")
        void shippedOrderCannotBeCancelled() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();
            pay(orderId);

            Long itemId = firstItemId(orderId);
            advance(itemId, "PROCESSING");
            advance(itemId, "SHIPPED");

            mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("reason", "پشیمان شدم"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("لغو یک قلم توسط فروشنده، مبلغ سفارش را کم می‌کند")
        void itemCancellationReducesTotal() throws Exception {
            addToCart(product.getId(), 2);
            Long orderId = placeOrder();
            pay(orderId);

            Order before = orderRepository.findById(orderId).orElseThrow();
            BigDecimal originalTotal = before.getTotalAmount();

            Long itemId = firstItemId(orderId);

            mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/cancel")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("reason", "کالا آسیب دیده"))))
                    .andExpect(status().isOk());

            Order after = orderRepository.findById(orderId).orElseThrow();

            assertThat(after.getTotalAmount()).isLessThan(originalTotal);
            assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    // ==================== دسترسی به سفارش ====================

    @Nested
    @DisplayName("دسترسی به سفارش")
    class OrderAccess {

        @Test
        @DisplayName("سفارش کاربر دیگر ۴۰۴ می‌دهد")
        void othersOrderReturnsNotFound() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            User otherCustomer = createUser("buyer2@test.local", "خریدار دوم",
                    "USER", UserStatus.ACTIVE);

            mockMvc.perform(get("/api/v1/orders/" + orderId)
                            .header("Authorization", bearerFor(otherCustomer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("مدیر همه سفارش‌ها را با اطلاعات مشتری می‌بیند")
        void managerSeesAllOrders() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(get("/api/v1/orders/" + orderId)
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/v1/admin/orders")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].customer").isNotEmpty());
        }
    }

    // ==================== کمکی ====================

    private void addToCart(Long productId, int quantity) throws Exception {
        mockMvc.perform(post("/api/v1/carts/me/items")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of(
                                "productId", productId,
                                "quantity", quantity))))
                .andExpect(status().isOk());
    }

    private Long placeOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of("addressId", address.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("order").get("id").asLong();
    }

    private void pay(Long orderId) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/payment")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of("simulateSuccess", true))))
                .andExpect(status().isOk());
    }

    private void cancel(Long orderId) throws Exception {
        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/cancel")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of("reason", "تست لغو"))))
                .andExpect(status().isOk());
    }

    private void advance(Long itemId, String status) throws Exception {
        mockMvc.perform(patch("/api/v1/seller/orders/items/" + itemId + "/status")
                        .header("Authorization", bearerFor(seller))
                        .contentType("application/json")
                        .content(json(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private Long firstItemId(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow()
                .getItems().get(0).getId();
    }

    /** سفارش پرداخت‌شده می‌سازد و شناسه اولین قلمش را برمی‌گرداند */
    private Long paidOrderItem() throws Exception {
        addToCart(product.getId(), 1);
        Long orderId = placeOrder();
        pay(orderId);
        return firstItemId(orderId);
    }
}