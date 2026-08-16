package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.product", "items.product.category",
                                   "items.product.seller", "items.product.images"})
    Optional<Cart> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}