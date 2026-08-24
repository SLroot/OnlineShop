package com.shop.online_shop.repository;

import com.shop.online_shop.entity.OrderItem;
import com.shop.online_shop.entity.OrderItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = {"order", "order.user", "product", "seller",
                                   "seller.sellerProfile"})
    Page<OrderItem> findBySellerId(Long sellerId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.user", "product", "seller",
                                   "seller.sellerProfile"})
    Page<OrderItem> findBySellerIdAndStatus(Long sellerId, OrderItemStatus status,
                                            Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.user", "product", "seller",
                                   "seller.sellerProfile"})
    Page<OrderItem> findByStatus(OrderItemStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"order", "order.user", "product", "seller",
                                   "seller.sellerProfile"})
    Page<OrderItem> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"order", "order.user", "product", "seller",
                                   "seller.sellerProfile"})
    Optional<OrderItem> findById(Long id);

    /** مالکیت در خود کوئری شرط می‌شود تا قلم دیگران اصلاً یافت نشود */
    @EntityGraph(attributePaths = {"order", "order.user", "product", "seller",
                                   "seller.sellerProfile"})
    Optional<OrderItem> findByIdAndSellerId(Long id, Long sellerId);
}