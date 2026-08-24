package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.CategoryRequest;
import com.shop.online_shop.dto.response.CategoryResponse;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "دسته‌بندی محصولات — یک مسیر برای همه نقش‌ها")
public class CategoryController {

    private final CategoryService categoryService;

    // ==================== خواندن — عمومی ====================

    @GetMapping
    @Operation(summary = "درخت دسته‌بندی‌ها",
               description = "ساختار سلسله‌مراتبی تا حداکثر سه سطح — بدون نیاز به ورود")
    public ResponseEntity<List<CategoryResponse>> tree() {
        return ResponseEntity.ok(categoryService.getTreeAsResponse());
    }

    @GetMapping("/{id}")
    @Operation(summary = "جزئیات یک دسته‌بندی به‌همراه زیرمجموعه‌هایش")
    public ResponseEntity<CategoryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getByIdAsResponse(id));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "جستجوی دسته‌بندی با slug")
    public ResponseEntity<CategoryResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getBySlugAsResponse(slug));
    }

    // ==================== نوشتن — نیازمند مجوز ====================

    @PostMapping
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Operation(summary = "افزودن دسته‌بندی",
               description = "برای دسته‌بندی ریشه، parentId را خالی بگذارید. حداکثر سه سطح",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "ساخته شد"),
        @ApiResponse(responseCode = "400", description = "والد نامعتبر یا عمق بیش از حد"),
        @ApiResponse(responseCode = "403", description = "مجوز CATEGORY_MANAGE ندارید")
    })
    public ResponseEntity<CategoryResponse> create(
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var created = categoryService.create(request, me.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CategoryResponse.flat(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Operation(summary = "ویرایش دسته‌بندی",
               description = "نام و توضیحات قابل تغییر است؛ جابه‌جایی در درخت پشتیبانی نمی‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                CategoryResponse.flat(categoryService.update(id, request, me.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATEGORY_MANAGE')")
    @Operation(summary = "حذف دسته‌بندی",
               description = "تنها اگر نه زیرمجموعه دارد نه محصول",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "حذف شد"),
        @ApiResponse(responseCode = "409", description = "زیرمجموعه یا محصول دارد")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        categoryService.delete(id, me.getId());
        return ResponseEntity.noContent().build();
    }
}