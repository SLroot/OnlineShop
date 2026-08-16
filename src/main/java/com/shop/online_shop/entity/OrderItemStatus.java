package com.shop.online_shop.entity;

/** وضعیت هر قلم سفارش — هر فروشنده اقلام خودش را مدیریت می‌کند */
public enum OrderItemStatus {
    PENDING_PAYMENT(0),
    PAID(1),
    PROCESSING(2),
    SHIPPED(3),
    DELIVERED(4),
    CANCELLED(-1);

    /** برای محاسبه وضعیت کلی سفارش — کمترین پیشرفت تعیین‌کننده است */
    private final int progress;

    OrderItemStatus(int progress) {
        this.progress = progress;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isCancellableByCustomer() {
        return this == PENDING_PAYMENT || this == PAID || this == PROCESSING;
    }

    public boolean isActive() {
        return this != CANCELLED;
    }
}