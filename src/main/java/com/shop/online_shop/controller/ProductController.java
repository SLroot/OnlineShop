package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.ProductResponse;
import com.shop.online_shop.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "مشاهده و جستجوی محصولات — عمومی")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "لیست محصولات",
               description = "فیلتر بر اساس دسته‌بندی (شامل زیرمجموعه‌ها)، "
                           + "بازه قیمت و موجودی. جستجو روی نام محصول")
    public ResponseEntity<PagedResponse<ProductResponse>> list(
            @Parameter(description = "جستجو در نام محصول")
            @RequestParam(required = false) String q,

            @Parameter(description = "شناسه دسته‌بندی — زیرمجموعه‌ها هم شامل می‌شوند")
            @RequestParam(required = false) Long categoryId,

            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "فقط محصولات موجود")
            @RequestParam(required = false) Boolean inStock,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,

            @Parameter(description = "createdAt | price | name")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "asc | desc")
            @RequestParam(defaultValue = "desc") String direction) {

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy);

        var result = productService.search(q, categoryId, minPrice, maxPrice, inStock,
                PageRequest.of(page, size, sort));

        return ResponseEntity.ok(PagedResponse.from(result, ProductResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "جزئیات محصول")
    public ResponseEntity<ProductResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ProductResponse.from(productService.getPublicById(id)));
    }
}