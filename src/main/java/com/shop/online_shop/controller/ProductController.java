package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.ProductRequest;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.ProductResponse;
import com.shop.online_shop.security.Scope;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

/**
 * کاتالوگ محصولات.
 *
 * دسترسی در دو لایه بررسی می‌شود:
 * PreAuthorize تعیین می‌کند آیا کاربر اجازه این نوع عملیات را دارد،
 * و AccessGuard درون سرویس تعیین می‌کند آیا روی این رکورد خاص حق دارد.
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "کاتالوگ محصولات — یک مسیر برای همه نقش‌ها")
public class ProductController {

    private final ProductService productService;

    // ==================== فهرست و جزئیات ====================

    @GetMapping
    @Operation(summary = "فهرست محصولات",
               description = "دامنه دید با پارامتر scope تعیین می‌شود. "
                           + "public محصولات فعال بدون نیاز به ورود، "
                           + "mine محصولات خودم شامل غیرفعال‌ها که نیازمند PRODUCT_READ_OWN است، "
                           + "all محصولات همه فروشندگان که نیازمند PRODUCT_MANAGE_ALL است")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "موفق"),
        @ApiResponse(responseCode = "400", description = "مقدار scope نامعتبر"),
        @ApiResponse(responseCode = "403", description = "مجوز لازم برای این دامنه را ندارید")
    })
    public ResponseEntity<PagedResponse<ProductResponse>> list(
            @Parameter(description = "public | mine | all")
            @RequestParam(required = false) String scope,

            @Parameter(description = "جستجو در نام محصول")
            @RequestParam(required = false) String q,

            @Parameter(description = "شناسه دسته‌بندی — زیرمجموعه‌ها هم شامل می‌شوند")
            @RequestParam(required = false) Long categoryId,

            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,

            @Parameter(description = "فقط محصولات موجود")
            @RequestParam(required = false) Boolean inStock,

            @Parameter(description = "فیلتر فروشنده — تنها در دامنه all")
            @RequestParam(required = false) Long sellerId,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,

            @Parameter(description = "createdAt | price | name")
            @RequestParam(defaultValue = "createdAt") String sortBy,

            @Parameter(description = "asc | desc")
            @RequestParam(defaultValue = "desc") String direction,

            @AuthenticationPrincipal UserPrincipal me) {

        Sort sort = Sort.by(
                "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC,
                sortBy);

        var filter = new ProductService.ProductFilter(
                q, categoryId, minPrice, maxPrice, inStock, sellerId);

        var result = productService.list(Scope.parse(scope), filter, me,
                PageRequest.of(page, size, sort));

        return ResponseEntity.ok(PagedResponse.from(result, ProductResponse::from));
    }

    @GetMapping("/{id}")
    @Operation(summary = "جزئیات محصول",
               description = "محصول غیرفعال تنها برای مالک و دارنده مجوز مدیریت سراسری "
                           + "قابل مشاهده است")
    public ResponseEntity<ProductResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(ProductResponse.from(productService.getById(id, me)));
    }

    // ==================== ساخت و ویرایش ====================

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PRODUCT_CREATE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "افزودن محصول",
               description = "دسته‌بندی باید پایین‌ترین سطح باشد. دارنده PRODUCT_MANAGE_ALL "
                           + "می‌تواند با ارسال sellerId محصول را به نام فروشنده دیگری ثبت کند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "ساخته شد"),
        @ApiResponse(responseCode = "400", description = "دسته‌بندی یا فروشنده نامعتبر"),
        @ApiResponse(responseCode = "403", description = "مجوز لازم را ندارید"),
        @ApiResponse(responseCode = "409", description = "کد کالا تکراری")
    })
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var created = productService.create(request, me);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ProductResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "ویرایش محصول",
               description = "فروشنده تنها محصول خودش، دارنده PRODUCT_MANAGE_ALL همه را. "
                           + "مالک محصول با ویرایش تغییر نمی‌کند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ویرایش شد"),
        @ApiResponse(responseCode = "403", description = "محصول متعلق به شما نیست")
    })
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                ProductResponse.from(productService.update(id, request, me)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_DELETE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "غیرفعال‌سازی محصول",
               description = "حذف واقعی نمی‌شود چون سفارش‌های گذشته به آن ارجاع دارند. "
                           + "محصول از سبد خرید همه کاربران نیز حذف می‌گردد",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        productService.deactivate(id, me);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "فعال‌سازی مجدد محصول",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProductResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(ProductResponse.from(productService.activate(id, me)));
    }

    // ==================== تصاویر ====================

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "افزودن تصویر",
               description = "حداکثر سه تصویر، هر کدام تا دو مگابایت، با فرمت "
                           + "JPEG، PNG یا WebP. نخستین تصویر خودکار اصلی می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "تصویر اضافه شد"),
        @ApiResponse(responseCode = "400", description = "فرمت نامعتبر، حجم زیاد یا سقف تصاویر"),
        @ApiResponse(responseCode = "403", description = "محصول متعلق به شما نیست")
    })
    public ResponseEntity<ProductResponse> addImage(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean primary,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                ProductResponse.from(productService.addImage(id, file, primary, me)));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "حذف تصویر",
               description = "اگر تصویر اصلی حذف شود، نخستین تصویر باقی‌مانده جایگزین می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long id,
            @PathVariable Long imageId,
            @AuthenticationPrincipal UserPrincipal me) {

        productService.deleteImage(id, imageId, me);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("hasAnyAuthority('PRODUCT_UPDATE', 'PRODUCT_MANAGE_ALL')")
    @Operation(summary = "تعیین تصویر اصلی",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProductResponse> setPrimary(
            @PathVariable Long id,
            @PathVariable Long imageId,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                ProductResponse.from(productService.setPrimaryImage(id, imageId, me)));
    }
}