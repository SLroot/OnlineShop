package com.shop.online_shop.entity;

/** وضعیت کلی سفارش — از روی وضعیت اقلام محاسبه می‌شود */
public enum OrderStatus {
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    PARTIALLY_SHIPPED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}