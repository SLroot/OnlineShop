package com.shop.online_shop.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryScheduler {

    private final OrderService orderService;

    /** هر ۲ دقیقه سفارش‌های پرداخت‌نشده منقضی را لغو می‌کند */
    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void cancelExpiredOrders() {
        try {
            orderService.cancelExpiredOrders();
        } catch (Exception e) {
            log.error("Order expiry job failed: {}", e.getMessage(), e);
        }
    }
}