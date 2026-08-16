package com.shop.online_shop.repository;

import com.shop.online_shop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    long countByProductId(Long productId);

    @Modifying
    @Query("UPDATE ProductImage i SET i.primary = false WHERE i.product.id = :productId")
    void clearPrimaryFlags(@Param("productId") Long productId);
}