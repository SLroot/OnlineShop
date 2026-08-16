package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.user"})
    Page<Payment> findAll(Pageable pageable);
}