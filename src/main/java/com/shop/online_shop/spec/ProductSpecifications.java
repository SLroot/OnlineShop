package com.shop.online_shop.spec;

import com.shop.online_shop.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * قطعات ترکیب‌پذیر برای ساخت کوئری فیلتر.
 * هر متد null برمی‌گرداند اگر آن فیلتر اعمال نشود.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> isActive() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    public static Specification<Product> nameContains(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    /** شامل همه زیرمجموعه‌های دسته‌بندی انتخاب‌شده */
    public static Specification<Product> inCategories(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return null;
        return (root, query, cb) -> root.get("category").get("id").in(categoryIds);
    }

    public static Specification<Product> priceAtLeast(BigDecimal min) {
        if (min == null) return null;
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), min);
    }

    public static Specification<Product> priceAtMost(BigDecimal max) {
        if (max == null) return null;
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), max);
    }

    public static Specification<Product> inStockOnly(Boolean onlyInStock) {
        if (onlyInStock == null || !onlyInStock) return null;
        return (root, query, cb) -> cb.greaterThan(root.get("stock"), 0);
    }

    public static Specification<Product> bySeller(Long sellerId) {
        if (sellerId == null) return null;
        return (root, query, cb) -> cb.equal(root.get("seller").get("id"), sellerId);
    }
}
