package com.shop.online_shop;

import com.shop.online_shop.entity.Address;
import com.shop.online_shop.entity.Cart;
import com.shop.online_shop.entity.Category;
import com.shop.online_shop.entity.Order;
import com.shop.online_shop.entity.OrderItem;
import com.shop.online_shop.entity.OrderStatus;
import com.shop.online_shop.entity.Payment;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.repository.AddressRepository;
import com.shop.online_shop.repository.CartRepository;
import com.shop.online_shop.repository.CategoryRepository;
import com.shop.online_shop.repository.OrderRepository;
import com.shop.online_shop.repository.PaymentRepository;
import com.shop.online_shop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private static final String PRODUCTS = "/api/v1/products";
    private static final String CARTS = "/api/v1/carts/me";
    private static final String ORDERS = "/api/v1/orders";
    private static final String ORDER_ITEMS = "/api/v1/order-items";

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
            mockMvc.perform(get(CARTS).header("Authorization", bearerFor(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.empty").value(true))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }

        @Test
        @DisplayName("افزودن دوباره همان محصول، تعداد را جمع می‌کند")
        void addingSameProductAccumulates() throws Exception {
            addToCart(product.getId(), 2);
            addToCart(product.getId(), 3);

            mockMvc.perform(get(CARTS).header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.itemCount").value(1))
                    .andExpect(jsonPath("$.totalQuantity").value(5));
        }

        @Test
        @DisplayName("تعداد بیش از موجودی، به موجودی محدود می‌شود")
        void quantityIsCappedToStock() throws Exception {
            mockMvc.perform(post(CARTS + "/items")
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

            mockMvc.perform(delete(PRODUCTS + "/" + product.getId())
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get(CARTS).header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("سبد، موجودی محصول را رزرو نمی‌کند")
        void cartDoesNotReserveStock() throws Exception {
            addToCart(product.getId(), 5);

            Product fresh = productRepository.findById(product.getId()).orElseThrow();
            assertThat(fresh.getStock()).isEqualTo(INITIAL_STOCK);
        }

        @Test
        @DisplayName("مدیر می‌تواند سبد یک کاربر را ببیند")
        void managerCanViewUserCart() throws Exception {
            addToCart(product.getId(), 2);

            mockMvc.perform(get("/api/v1/carts/users/" + customer.getId())
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itemCount").value(1));
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

            mockMvc.perform(get(CARTS).header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.empty").value(true));
        }

        @Test
        @DisplayName("سفارش با وضعیت در انتظار پرداخت و مهلت ساخته می‌شود")
        void orderStartsPendingPayment() throws Exception {
            addToCart(product.getId(), 1);

            mockMvc.perform(post(ORDERS)
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
            mockMvc.perform(post(ORDERS)
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("addressId", address.getId()))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند سفارش ثبت کند")
        void sellerCannotPlaceOrder() throws Exception {
            mockMvc.perform(post(ORDERS)
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

            mockMvc.perform(post(ORDERS + "/" + orderId + "/payment")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("simulateSuccess", true))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.transactionRef").isNotEmpty());

            mockMvc.perform(get(ORDERS + "/" + orderId)
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.items[0].status").value("PAID"));
        }

        @Test
        @DisplayName("پرداخت ناموفق، سفارش را لغو نمی‌کند")
        void failedPaymentKeepsOrderOpen() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(post(ORDERS + "/" + orderId + "/payment")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("simulateSuccess", false))))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(get(ORDERS + "/" + orderId)
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
        }

        @Test
        @DisplayName("پرداخت دوباره رد می‌شود")
        void doublePaymentIsRejected() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();
            pay(orderId);

            mockMvc.perform(post(ORDERS + "/" + orderId + "/payment")
                            .header("Authorization", bearerFor(customer))
                            .contentType("application/json")
                            .content(json(Map.of("simulateSuccess", true))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("مدیر فهرست همه پرداخت‌ها را می‌بیند")
        void managerSeesAllPayments() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();
            pay(orderId);

            mockMvc.perform(get("/api/v1/payments")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("مشتری نمی‌تواند فهرست همه پرداخت‌ها را ببیند")
        void customerCannotSeeAllPayments() throws Exception {
            mockMvc.perform(get("/api/v1/payments")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isForbidden());
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

            mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/status")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("status", "PROCESSING"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PROCESSING"));
        }

        @Test
        @DisplayName("فروشنده تنها اقلام خودش را در فهرست می‌بیند")
        void sellerSeesOnlyOwnItems() throws Exception {
            paidOrderItem();

            mockMvc.perform(get(ORDER_ITEMS)
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1));
        }

        @Test
        @DisplayName("مدیر اقلام همه فروشندگان را می‌بیند")
        void managerSeesAllItems() throws Exception {
            paidOrderItem();

            mockMvc.perform(get(ORDER_ITEMS + "?scope=all")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("فروشنده نمی‌تواند دامنه all را ببیند")
        void sellerCannotUseAllScopeOnItems() throws Exception {
            mockMvc.perform(get(ORDER_ITEMS + "?scope=all")
                            .header("Authorization", bearerFor(seller)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("پرش از مراحل مجاز نیست")
        void cannotSkipStages() throws Exception {
            Long itemId = paidOrderItem();

            mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/status")
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

            mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/status")
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

            mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/status")
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
                    RoleCode.SELLER, UserStatus.ACTIVE);

            mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/status")
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

            mockMvc.perform(patch(ORDERS + "/" + orderId + "/cancel")
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

            mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/cancel")
                            .header("Authorization", bearerFor(seller))
                            .contentType("application/json")
                            .content(json(Map.of("reason", "کالا آسیب دیده"))))
                    .andExpect(status().isOk());

            Order after = orderRepository.findById(orderId).orElseThrow();

            assertThat(after.getTotalAmount()).isLessThan(originalTotal);
            assertThat(after.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    // ==================== دسترسی و شکل پاسخ ====================

    @Nested
    @DisplayName("دسترسی به سفارش")
    class OrderAccess {

        @Test
        @DisplayName("سفارش کاربر دیگر ۴۰۴ می‌دهد")
        void othersOrderReturnsNotFound() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            User otherCustomer = createUser("buyer2@test.local", "خریدار دوم",
                    RoleCode.USER, UserStatus.ACTIVE);

            mockMvc.perform(get(ORDERS + "/" + orderId)
                            .header("Authorization", bearerFor(otherCustomer)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("مشتری بخش اطلاعات مشتری را در پاسخ نمی‌بیند")
        void customerDoesNotSeeCustomerBlock() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(get(ORDERS + "/" + orderId)
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customer").doesNotExist());
        }

        @Test
        @DisplayName("مدیر همه سفارش‌ها را با اطلاعات مشتری می‌بیند")
        void managerSeesAllOrders() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(get(ORDERS + "/" + orderId)
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customer").isNotEmpty());

            mockMvc.perform(get(ORDERS + "?scope=all")
                            .header("Authorization", bearerFor(manager)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].customer").isNotEmpty());
        }

        @Test
        @DisplayName("مشتری نمی‌تواند دامنه all را ببیند")
        void customerCannotUseAllScope() throws Exception {
            mockMvc.perform(get(ORDERS + "?scope=all")
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("پاسخ سفارش، ایمیل فروشنده را افشا نمی‌کند")
        void sellerContactIsNotExposed() throws Exception {
            addToCart(product.getId(), 1);
            Long orderId = placeOrder();

            mockMvc.perform(get(ORDERS + "/" + orderId)
                            .header("Authorization", bearerFor(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].seller.shopName").exists())
                    .andExpect(jsonPath("$.items[0].seller.email").doesNotExist());
        }
    }

    // ==================== کمکی ====================

    private void addToCart(Long productId, int quantity) throws Exception {
        mockMvc.perform(post(CARTS + "/items")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of(
                                "productId", productId,
                                "quantity", quantity))))
                .andExpect(status().isOk());
    }

    private Long placeOrder() throws Exception {
        String response = mockMvc.perform(post(ORDERS)
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of("addressId", address.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("order").get("id").asLong();
    }

    private void pay(Long orderId) throws Exception {
        mockMvc.perform(post(ORDERS + "/" + orderId + "/payment")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of("simulateSuccess", true))))
                .andExpect(status().isOk());
    }

    private void cancel(Long orderId) throws Exception {
        mockMvc.perform(patch(ORDERS + "/" + orderId + "/cancel")
                        .header("Authorization", bearerFor(customer))
                        .contentType("application/json")
                        .content(json(Map.of("reason", "تست لغو"))))
                .andExpect(status().isOk());
    }

    private void advance(Long itemId, String status) throws Exception {
        mockMvc.perform(patch(ORDER_ITEMS + "/" + itemId + "/status")
                        .header("Authorization", bearerFor(seller))
                        .contentType("application/json")
                        .content(json(Map.of("status", status))))
                .andExpect(status().isOk());
    }

    private Long firstItemId(Long orderId) {
        return orderRepository.findById(orderId).orElseThrow()
                .getItems().get(0).getId();
    }

    /** سفارش پرداخت‌شده می‌سازد و شناسه نخستین قلمش را برمی‌گرداند */
    private Long paidOrderItem() throws Exception {
        addToCart(product.getId(), 1);
        Long orderId = placeOrder();
        pay(orderId);
        return firstItemId(orderId);
    }
}