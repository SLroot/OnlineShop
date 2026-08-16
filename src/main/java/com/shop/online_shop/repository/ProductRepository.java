package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    // ==================== خواندن ====================
    // EntityGraph لازم است چون open-in-view خاموش است و
    // تبدیل به DTO خارج از تراکنش به روابط lazy دسترسی دارد

    @EntityGraph(attributePaths = {"category", "seller", "images"})
    Optional<Product> findByIdAndActiveTrue(Long id);

    @EntityGraph(attributePaths = {"category", "seller", "images"})
    Page<Product> findBySellerId(Long sellerId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "seller", "images"})
    Optional<Product> findById(Long id);

    @Override
    @EntityGraph(attributePaths = {"category", "seller", "images"})
    Page<Product> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "seller", "images"})
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    // ==================== کسر و بازگشت موجودی ====================

    /**
     * قفل بدبینانه روی ردیف محصول.
     * بدون این، دو سفارش همزمان می‌توانند موجودی را منفی کنند:
     * هر دو مقدار قدیمی را می‌خوانند و هر دو کسر می‌کنند.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    // ==================== تعلیق فروشنده ====================

    /**
     * فقط محصولات فعال علامت می‌خورند تا هنگام رفع تعلیق،
     * محصولاتی که خود فروشنده غیرفعال کرده بود دست‌نخورده بمانند.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.active = false, p.deactivatedBySuspension = true " +
           "WHERE p.seller.id = :sellerId AND p.active = true")
    int suspendSellerProducts(@Param("sellerId") Long sellerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.active = true, p.deactivatedBySuspension = false " +
           "WHERE p.seller.id = :sellerId AND p.deactivatedBySuspension = true")
    int restoreSellerProducts(@Param("sellerId") Long sellerId);
}