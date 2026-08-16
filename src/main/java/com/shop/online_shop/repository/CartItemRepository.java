package com.shop.online_shop.repository;

import com.shop.online_shop.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /**
     * حذف یک محصول از سبد همه کاربران.
     * هنگام غیرفعال شدن محصول یا تعلیق فروشنده صدا زده می‌شود.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CartItem ci WHERE ci.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);

    /** حذف همه محصولات یک فروشنده از سبد همه کاربران */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CartItem ci WHERE ci.product.id IN " +
           "(SELECT p.id FROM Product p WHERE p.seller.id = :sellerId)")
    int deleteBySellerId(@Param("sellerId") Long sellerId);
}