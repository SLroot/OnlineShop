package com.shop.online_shop.dto.response;

import com.shop.online_shop.entity.Category;

import java.util.List;

public record CategoryResponse(
    Long id,
    String name,
    String slug,
    String description,
    Long parentId,
    Integer depth,
    boolean leaf,
    List<CategoryResponse> children
) {
    /**
     * بدون زیرمجموعه — برای پاسخ عملیات ساخت و ویرایش.
     * ساخت درخت در CategoryService انجام می‌شود، جایی که تراکنش باز است.
     */
    public static CategoryResponse flat(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getSlug(),
                c.getDescription(),
                c.getParent() != null ? c.getParent().getId() : null,
                c.getDepth(),
                true,
                null
        );
    }
}