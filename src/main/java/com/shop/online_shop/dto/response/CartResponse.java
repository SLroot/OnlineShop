package com.shop.online_shop.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
    Long id,
    List<CartItemResponse> items,
    int itemCount,
    int totalQuantity,
    BigDecimal subtotal,
    boolean empty,
    String message,
    List<String> notices
) {
    public record CartItemResponse(
        Long id,
        Long productId,
        String productName,
        String sku,
        String imageUrl,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        Integer availableStock,
        boolean quantityAdjusted
    ) {}
}