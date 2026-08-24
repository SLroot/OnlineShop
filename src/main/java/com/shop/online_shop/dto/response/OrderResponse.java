package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Order;
import com.shop.online_shop.entity.OrderItem;
import com.shop.online_shop.entity.Payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record OrderResponse(
    Long id,
    String status,
    BigDecimal totalAmount,
    String shippingAddress,
    List<OrderItemResponse> items,
    PaymentInfo payment,
    Instant paymentDeadline,
    String cancellationReason,
    CustomerInfo customer,
    Instant createdAt
) {
    public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        String productSku,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        String status,
        String cancellationReason,
        SellerInfo seller
    ) {}

    /** تنها اطلاعات عمومی فروشگاه — ایمیل و تلفن فروشنده افشا نمی‌شود */
    public record SellerInfo(Long id, String shopName) {}

    public record CustomerInfo(Long id, String fullName, String email, String phone) {}

    public record PaymentInfo(
        Long id, String status, BigDecimal amount, String transactionRef,
        String failureReason, Instant paidAt, Instant refundedAt
    ) {}

    /**
     * ساخت پاسخ بر اساس مجوزهای درخواست‌کننده.
     * بخش مشتری تنها زمانی پر می‌شود که بیننده مجوز دیدن سفارش‌های
     * همه را داشته باشد؛ صاحب سفارش به آن نیازی ندارد.
     */
    public static OrderResponse from(Order order, boolean includeCustomer) {
        List<OrderItemResponse> items = order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getId))
                .map(OrderResponse::toItem)
                .toList();

        Payment p = order.getPayment();

        PaymentInfo paymentInfo = p == null ? null : new PaymentInfo(
                p.getId(), p.getStatus().name(), p.getAmount(),
                p.getTransactionRef(), p.getFailureReason(),
                p.getPaidAt(), p.getRefundedAt());

        CustomerInfo customer = includeCustomer
                ? new CustomerInfo(order.getUser().getId(),
                                   order.getUser().getFullName(),
                                   order.getUser().getEmail(),
                                   order.getUser().getPhone())
                : null;

        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                items,
                paymentInfo,
                order.getPaymentDeadline(),
                order.getCancellationReason(),
                customer,
                order.getCreatedAt());
    }

    private static OrderItemResponse toItem(OrderItem item) {
        String shopName = item.getSeller().getSellerProfile() != null
                ? item.getSeller().getSellerProfile().getShopName()
                : item.getSeller().getFullName();

        return new OrderItemResponse(
                item.getId(),
                item.getProduct().getId(),
                item.getProductName(),
                item.getProductSku(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal(),
                item.getStatus().name(),
                item.getCancellationReason(),
                new SellerInfo(item.getSeller().getId(), shopName));
    }
}