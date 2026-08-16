package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Order;
import com.shop.online_shop.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Override
    @EntityGraph(attributePaths = {"items", "items.seller", "items.product",
                                   "payment", "user"})
    Optional<Order> findById(Long id);

    @EntityGraph(attributePaths = {"items", "items.seller", "payment"})
    Page<Order> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"items", "items.seller", "payment", "user"})
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"items", "items.seller", "payment", "user"})
    Page<Order> findAll(Pageable pageable);

    /** سفارش‌هایی که مهلت پرداختشان گذشته — برای زمان‌بند لغو خودکار */
    @EntityGraph(attributePaths = {"items", "items.product"})
    @Query("SELECT o FROM Order o WHERE o.status = com.shop.online_shop.entity.OrderStatus.PENDING_PAYMENT " +
           "AND o.paymentDeadline < :now")
    List<Order> findExpiredUnpaid(@Param("now") Instant now);
}