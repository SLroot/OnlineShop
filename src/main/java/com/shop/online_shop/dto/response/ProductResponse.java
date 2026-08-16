package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Product;
import com.shop.online_shop.entity.ProductImage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(
    Long id,
    String sku,
    String name,
    String description,
    BigDecimal price,
    Integer stock,
    boolean inStock,
    boolean active,
    CategoryRef category,
    SellerRef seller,
    String primaryImageUrl,
    List<ImageRef> images,
    Instant createdAt
) {
    public record CategoryRef(Long id, String name, String slug) {}
    public record SellerRef(Long id, String fullName) {}
    public record ImageRef(Long id, String url, boolean primary) {}

    public static ProductResponse from(Product p) {
        List<ImageRef> images = p.getImages().stream()
                .map(i -> new ImageRef(i.getId(), i.getUrl(), i.isPrimary()))
                .toList();

        // تصویر اصلی، یا اگر هیچ‌کدام اصلی نبود اولین تصویر
        String primaryUrl = images.stream()
                .filter(ImageRef::primary)
                .map(ImageRef::url)
                .findFirst()
                .orElseGet(() -> images.isEmpty() ? null : images.get(0).url());

        return new ProductResponse(
                p.getId(),
                p.getSku(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getStock(),
                p.isInStock(),
                p.isActive(),
                new CategoryRef(p.getCategory().getId(),
                                p.getCategory().getName(),
                                p.getCategory().getSlug()),
                new SellerRef(p.getSeller().getId(), p.getSeller().getFullName()),
                primaryUrl,
                images,
                p.getCreatedAt());
    }
}