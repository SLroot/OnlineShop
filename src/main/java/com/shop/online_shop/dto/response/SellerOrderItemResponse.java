package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.OrderItem;

import java.math.BigDecimal;
import java.time.Instant;

/** نمای فروشنده از یک قلم سفارش — فقط اطلاعات لازم برای انجام سفارش */
public record SellerOrderItemResponse(
    Long id,
    Long orderId,
    String productName,
    String productSku,
    BigDecimal unitPrice,
    Integer quantity,
    BigDecimal lineTotal,
    String status,
    String shippingAddress,
    String customerName,
    Instant orderedAt
) {
    public static SellerOrderItemResponse from(OrderItem item) {
        return new SellerOrderItemResponse(
                item.getId(),
                item.getOrder().getId(),
                item.getProductName(),
                item.getProductSku(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal(),
                item.getStatus().name(),
                item.getOrder().getShippingAddress(),
                item.getOrder().getUser().getFullName(),
                item.getOrder().getCreatedAt());
    }
}