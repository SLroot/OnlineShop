package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_images", indexes = {
    @Index(name = "idx_image_product", columnList = "product_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** نام فایل ذخیره‌شده روی دیسک */
    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    /** مسیر عمومی برای کلاینت */
    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = false;
}