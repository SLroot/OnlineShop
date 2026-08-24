package com.shop.online_shop.service;

import com.shop.online_shop.entity.Address;
import com.shop.online_shop.entity.Cart;
import com.shop.online_shop.entity.CartItem;
import com.shop.online_shop.entity.Order;
import com.shop.online_shop.entity.OrderItem;
import com.shop.online_shop.entity.OrderItemStatus;
import com.shop.online_shop.entity.OrderStatus;
import com.shop.online_shop.entity.Payment;
import com.shop.online_shop.entity.Product;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.CartRepository;
import com.shop.online_shop.repository.OrderItemRepository;
import com.shop.online_shop.repository.OrderRepository;
import com.shop.online_shop.repository.PaymentRepository;
import com.shop.online_shop.repository.ProductRepository;
import com.shop.online_shop.security.AccessGuard;
import com.shop.online_shop.security.Scope;
import com.shop.online_shop.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * منطق سفارش.
 * عمداً به PaymentService وابسته نیست تا حلقه وابستگی ایجاد نشود؛
 * هماهنگی بازپرداخت هنگام لغو در لایه کنترلر انجام می‌شود.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String READ_OWN = "ORDER_READ";
    private static final String READ_ALL = "ORDER_READ_ALL";
    private static final String FULFILL = "ORDER_FULFILL";
    private static final String UPDATE_ANY = "ORDER_UPDATE";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final CartRepository cartRepository;
    private final AddressService addressService;
    private final AuditLogService auditLogService;
    private final AccessGuard accessGuard;

    /** نتیجه ثبت سفارش، همراه با توضیح اقلامی که کنار گذاشته شدند */
    public record OrderResult(Order order, List<String> notices) {}

    // ==================== ثبت سفارش ====================

    /**
     * سفارش از کل سبد ساخته می‌شود.
     * اقلامی که ناموجود یا غیرفعال شده‌اند نادیده گرفته و از سبد حذف می‌شوند؛
     * بقیه ثبت می‌گردند. موجودی همین‌جا کسر می‌شود.
     */
    @Transactional
    public OrderResult placeOrder(Long addressId, UserPrincipal me) {
        Cart cart = cartRepository.findByUserId(me.getId())
                .orElseThrow(() -> ApiException.badRequest("سبد خرید یافت نشد"));

        if (cart.isEmpty()) {
            throw ApiException.badRequest("سبد خرید شما خالی است");
        }

        Address address = addressService.getMyAddress(addressId, me.getId());

        List<OrderItem> orderItems = new ArrayList<>();
        List<CartItem> consumed = new ArrayList<>();
        List<String> notices = new ArrayList<>();

        for (CartItem cartItem : new ArrayList<>(cart.getItems())) {
            Long productId = cartItem.getProduct().getId();

            // قفل ردیف محصول تا دو سفارش همزمان موجودی را منفی نکنند
            Product product = productRepository.findByIdForUpdate(productId).orElse(null);

            if (product == null || !product.isActive()) {
                notices.add(cartItem.getProduct().getName() + " — دیگر در دسترس نیست");
                consumed.add(cartItem);
                continue;
            }

            if (product.getStock() <= 0) {
                notices.add(product.getName() + " — ناموجود شد");
                consumed.add(cartItem);
                continue;
            }

            int quantity = Math.min(cartItem.getQuantity(), product.getStock());

            if (quantity < cartItem.getQuantity()) {
                notices.add(product.getName() + " — تنها " + quantity + " عدد موجود بود");
            }

            product.setStock(product.getStock() - quantity);
            productRepository.save(product);

            BigDecimal unitPrice = product.getPrice();

            orderItems.add(OrderItem.builder()
                    .product(product)
                    .seller(product.getSeller())
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .unitPrice(unitPrice)
                    .quantity(quantity)
                    .lineTotal(unitPrice.multiply(BigDecimal.valueOf(quantity)))
                    .status(OrderItemStatus.PENDING_PAYMENT)
                    .build());

            consumed.add(cartItem);
        }

        if (orderItems.isEmpty()) {
            cart.getItems().removeAll(consumed);
            cartRepository.save(cart);

            throw ApiException.badRequest(
                    "هیچ‌کدام از اقلام سبد قابل سفارش نبودند: " + String.join("، ", notices));
        }

        BigDecimal total = orderItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .user(cart.getUser())
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(total)
                .shippingAddress(address.toSnapshot())
                .recipientPhone(cart.getUser().getPhone())
                .paymentDeadline(Instant.now()
                        .plus(Order.PAYMENT_WINDOW_MINUTES, ChronoUnit.MINUTES))
                .build();

        orderItems.forEach(item -> item.setOrder(order));
        order.getItems().addAll(orderItems);

        Order saved = orderRepository.save(order);

        paymentRepository.save(Payment.builder()
                .order(saved)
                .amount(total)
                .status(Payment.PaymentStatus.PENDING)
                .build());

        cart.getItems().removeAll(consumed);
        cartRepository.save(cart);

        auditLogService.record(me.getId(), "ORDER_PLACED",
                "order: " + saved.getId() + " | items: " + orderItems.size()
                        + " | total: " + total);

        return new OrderResult(saved, notices);
    }

    // ==================== فهرست با دامنه دید ====================

    /**
     * یک نقطه ورود برای سفارش‌ها.
     * mine — سفارش‌های خود کاربر
     * all  — سفارش‌های همه، نیازمند ORDER_READ_ALL
     */
    @Transactional(readOnly = true)
    public Page<Order> list(Scope scope, OrderStatus status, Long userId,
                            UserPrincipal me, Pageable pageable) {

        return switch (scope) {

            case ALL -> {
                accessGuard.requireAuthority(me, READ_ALL);

                if (status != null) {
                    yield orderRepository.findByStatus(status, pageable);
                }
                yield userId != null
                        ? orderRepository.findByUserId(userId, pageable)
                        : orderRepository.findAll(pageable);
            }

            // دامنه عمومی برای سفارش معنا ندارد و به سفارش‌های خود کاربر برمی‌گردد
            case PUBLIC, MINE -> {
                accessGuard.requireAuthority(me, READ_OWN);

                yield status != null
                        ? orderRepository.findByUserIdAndStatus(me.getId(), status, pageable)
                        : orderRepository.findByUserId(me.getId(), pageable);
            }
        };
    }

    /**
     * اقلام سفارش از دید فروشنده.
     * mine — اقلام مربوط به محصولات خود فروشنده
     * all  — اقلام همه فروشندگان، نیازمند ORDER_READ_ALL
     */
    @Transactional(readOnly = true)
    public Page<OrderItem> listItems(Scope scope, OrderItemStatus status, Long sellerId,
                                     UserPrincipal me, Pageable pageable) {

        Long targetSeller = switch (scope) {
            case ALL -> {
                accessGuard.requireAuthority(me, READ_ALL);
                yield sellerId;                 // null یعنی همه فروشندگان
            }
            case PUBLIC, MINE -> {
                accessGuard.requireAuthority(me, FULFILL);
                yield me.getId();
            }
        };

        if (targetSeller == null) {
            return status != null
                    ? orderItemRepository.findByStatus(status, pageable)
                    : orderItemRepository.findAll(pageable);
        }

        return status != null
                ? orderItemRepository.findBySellerIdAndStatus(targetSeller, status, pageable)
                : orderItemRepository.findBySellerId(targetSeller, pageable);
    }

    /** صاحب سفارش یا کسی که مجوز دیدن همه را دارد */
    @Transactional(readOnly = true)
    public Order getOrder(Long orderId, UserPrincipal me) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("سفارش یافت نشد"));

        boolean isOwner = order.getUser().getId().equals(me.getId());

        if (!isOwner && !me.hasAuthority(READ_ALL)) {
            // ۴۰۴ به جای ۴۰۳ تا وجود سفارش لو نرود
            throw ApiException.notFound("سفارش یافت نشد");
        }
        return order;
    }

    // ==================== تغییر وضعیت ====================

    @Transactional
    public OrderItem updateItemStatus(Long itemId, OrderItemStatus newStatus,
                                      UserPrincipal me) {
        OrderItem item = resolveItemForManagement(itemId, me);

        assertValidTransition(item.getStatus(), newStatus);

        item.setStatus(newStatus);
        orderItemRepository.save(item);

        recalculateOrderStatus(item.getOrder());

        auditLogService.record(me.getId(), "ORDER_ITEM_STATUS_CHANGED",
                "item: " + itemId + " -> " + newStatus);

        return item;
    }

    @Transactional
    public OrderItem cancelItem(Long itemId, String reason, UserPrincipal me) {
        OrderItem item = resolveItemForManagement(itemId, me);

        if (item.getStatus() == OrderItemStatus.CANCELLED) {
            throw ApiException.badRequest("این قلم قبلاً لغو شده است");
        }
        if (item.getStatus() == OrderItemStatus.DELIVERED) {
            throw ApiException.badRequest("قلم تحویل‌شده قابل لغو نیست");
        }

        restoreStock(item);

        item.setStatus(OrderItemStatus.CANCELLED);
        item.setCancellationReason(reason);
        orderItemRepository.save(item);

        Order order = item.getOrder();
        recalculateTotal(order);
        recalculateOrderStatus(order);

        auditLogService.record(me.getId(), "ORDER_ITEM_CANCELLED",
                "item: " + itemId + " | " + reason);

        return item;
    }

    /**
     * لغو کل سفارش — تا پیش از ارسال.
     * بازپرداخت جداگانه توسط کنترلر صدا زده می‌شود.
     */
    @Transactional
    public Order cancelOrder(Long orderId, String reason, UserPrincipal me) {
        Order order = getOrder(orderId, me);

        boolean isOwner = order.getUser().getId().equals(me.getId());

        if (!isOwner && !me.hasAuthority(UPDATE_ANY)) {
            throw ApiException.forbidden("اجازه لغو این سفارش را ندارید");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("این سفارش قبلاً لغو شده است");
        }

        boolean anyCancellable = order.getItems().stream()
                .anyMatch(i -> i.getStatus().isCancellableByCustomer());

        if (!anyCancellable) {
            throw ApiException.badRequest("سفارش ارسال یا تحویل شده است و قابل لغو نیست");
        }

        for (OrderItem item : order.getItems()) {
            if (!item.getStatus().isCancellableByCustomer()) {
                continue;
            }
            restoreStock(item);
            item.setStatus(OrderItemStatus.CANCELLED);
            item.setCancellationReason(reason);
        }

        order.setCancellationReason(reason);
        recalculateTotal(order);
        recalculateOrderStatus(order);
        orderRepository.save(order);

        auditLogService.record(me.getId(), "ORDER_CANCELLED",
                "order: " + orderId + " | " + reason);

        return order;
    }

    // ==================== لغو خودکار ====================

    @Transactional
    public int cancelExpiredOrders() {
        List<Order> expired = orderRepository.findExpiredUnpaid(Instant.now());

        for (Order order : expired) {
            for (OrderItem item : order.getItems()) {
                if (item.getStatus() == OrderItemStatus.PENDING_PAYMENT) {
                    restoreStock(item);
                    item.setStatus(OrderItemStatus.CANCELLED);
                    item.setCancellationReason("مهلت پرداخت به پایان رسید");
                }
            }

            order.setStatus(OrderStatus.CANCELLED);
            order.setCancellationReason("مهلت پرداخت به پایان رسید");
            orderRepository.save(order);

            auditLogService.record(null, "ORDER_AUTO_CANCELLED", "order: " + order.getId());
        }

        if (!expired.isEmpty()) {
            log.info("Auto-cancelled {} unpaid orders", expired.size());
        }
        return expired.size();
    }

    // ==================== پس از پرداخت ====================

    @Transactional
    public void markAsPaid(Order order) {
        order.getItems().stream()
                .filter(i -> i.getStatus() == OrderItemStatus.PENDING_PAYMENT)
                .forEach(i -> i.setStatus(OrderItemStatus.PAID));

        recalculateOrderStatus(order);
        orderRepository.save(order);
    }

    // ==================== کمکی ====================

    private void restoreStock(OrderItem item) {
        productRepository.findByIdForUpdate(item.getProduct().getId())
                .ifPresent(product -> {
                    product.setStock(product.getStock() + item.getQuantity());
                    productRepository.save(product);
                });
    }

    /** مبلغ کل فقط از اقلام لغونشده محاسبه می‌شود */
    private void recalculateTotal(Order order) {
        BigDecimal total = order.getItems().stream()
                .filter(i -> i.getStatus().isActive())
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(total);
    }

    /**
     * وضعیت سفارش از روی اقلام محاسبه می‌شود.
     * اگر همه اقلام هم‌وضعیت باشند، همان وضعیت؛
     * اگر بخشی ارسال شده و بخشی نه، وضعیت «ارسال جزئی».
     */
    private void recalculateOrderStatus(Order order) {
        List<OrderItem> active = order.getItems().stream()
                .filter(i -> i.getStatus().isActive())
                .toList();

        if (active.isEmpty()) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            return;
        }

        int min = active.stream().mapToInt(i -> i.getStatus().getProgress()).min().orElse(0);
        int max = active.stream().mapToInt(i -> i.getStatus().getProgress()).max().orElse(0);

        OrderStatus status;

        if (min == max) {
            status = switch (min) {
                case 0 -> OrderStatus.PENDING_PAYMENT;
                case 1 -> OrderStatus.PAID;
                case 2 -> OrderStatus.PROCESSING;
                case 3 -> OrderStatus.SHIPPED;
                case 4 -> OrderStatus.DELIVERED;
                default -> OrderStatus.PROCESSING;
            };
        } else if (max >= 3) {
            status = OrderStatus.PARTIALLY_SHIPPED;
        } else {
            status = switch (min) {
                case 0 -> OrderStatus.PENDING_PAYMENT;
                case 1 -> OrderStatus.PAID;
                default -> OrderStatus.PROCESSING;
            };
        }

        order.setStatus(status);
        orderRepository.save(order);
    }

    /** فروشنده فقط روی اقلام خودش، دارنده مجوز سراسری روی همه */
    private OrderItem resolveItemForManagement(Long itemId, UserPrincipal me) {
        if (me.hasAuthority(UPDATE_ANY)) {
            return orderItemRepository.findById(itemId)
                    .orElseThrow(() -> ApiException.notFound("قلم سفارش یافت نشد"));
        }

        return orderItemRepository.findByIdAndSellerId(itemId, me.getId())
                .orElseThrow(() -> ApiException.notFound("قلم سفارش یافت نشد"));
    }

    /** جلوگیری از پرش یا عقبگرد در چرخه وضعیت */
    private void assertValidTransition(OrderItemStatus current, OrderItemStatus next) {
        if (current == OrderItemStatus.CANCELLED) {
            throw ApiException.badRequest("قلم لغوشده قابل تغییر وضعیت نیست");
        }
        if (next == OrderItemStatus.CANCELLED) {
            throw ApiException.badRequest("برای لغو از مسیر مخصوص لغو استفاده کنید");
        }
        if (current == OrderItemStatus.PENDING_PAYMENT) {
            throw ApiException.badRequest("تا پرداخت انجام نشود وضعیت قابل تغییر نیست");
        }
        if (next.getProgress() <= current.getProgress()) {
            throw ApiException.badRequest("بازگشت به وضعیت قبلی مجاز نیست");
        }
        if (next.getProgress() - current.getProgress() > 1) {
            throw ApiException.badRequest("پرش از مراحل مجاز نیست");
        }
    }
}