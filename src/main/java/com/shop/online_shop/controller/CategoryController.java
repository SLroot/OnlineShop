package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.CategoryResponse;
import com.shop.online_shop.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "مشاهده دسته‌بندی‌ها — عمومی")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "درخت دسته‌بندی‌ها",
               description = "ساختار سلسله‌مراتبی تا حداکثر ۳ سطح")
    public ResponseEntity<List<CategoryResponse>> tree() {
        return ResponseEntity.ok(categoryService.getTreeAsResponse());
    }

    @GetMapping("/{id}")
    @Operation(summary = "جزئیات یک دسته‌بندی",
               description = "به همراه زیرمجموعه‌هایش")
    public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getByIdAsResponse(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "جستجوی دسته‌بندی با slug")
    public ResponseEntity<CategoryResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getBySlugAsResponse(slug));
    }
}