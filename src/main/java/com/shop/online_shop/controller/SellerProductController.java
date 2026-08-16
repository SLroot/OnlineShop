package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.ProductRequest;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.ProductResponse;
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

@RestController
@RequestMapping("/api/v1/seller/products")
@RequiredArgsConstructor
@Tag(name = "Management — Products",
     description = "مدیریت محصولات — فروشنده روی محصولات خود، مدیر روی همه")
public class SellerProductController {

    private final ProductService productService;

    // ==================== لیست ====================

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ_OWN')")
    @Operation(summary = "محصولات من",
               description = "شامل محصولات غیرفعال",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<ProductResponse>> myProducts(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = productService.getOwnProducts(me.getId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, ProductResponse::from));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE_ALL')")
    @Operation(summary = "همه محصولات — مدیر",
               description = "شامل محصولات غیرفعال همه فروشندگان. "
                           + "با پارامتر sellerId می‌توان روی یک فروشنده فیلتر کرد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "موفق"),
        @ApiResponse(responseCode = "403", description = "مجوز مدیریت همه محصولات را ندارید")
    })
    public ResponseEntity<PagedResponse<ProductResponse>> allProducts(
            @Parameter(description = "فیلتر روی یک فروشنده خاص")
            @RequestParam(required = false) Long sellerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = productService.getAllForManagement(sellerId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, ProductResponse::from));
    }

    // ==================== ساخت و ویرایش ====================

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_CREATE')")
    @Operation(summary = "افزودن محصول",
               description = "دسته‌بندی باید پایین‌ترین سطح باشد. "
                           + "مدیر می‌تواند با ارسال sellerId محصول را به نام "
                           + "فروشنده دیگری ثبت کند؛ برای فروشنده این فیلد نادیده گرفته می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "ساخته شد"),
        @ApiResponse(responseCode = "400", description = "دسته‌بندی نامعتبر یا فروشنده نامعتبر"),
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
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "ویرایش محصول",
               description = "فروشنده فقط محصول خودش، مدیر همه محصولات. "
                           + "مالک محصول با ویرایش تغییر نمی‌کند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "ویرایش شد"),
        @ApiResponse(responseCode = "403", description = "محصول متعلق به شما نیست"),
        @ApiResponse(responseCode = "409", description = "کد کالا تکراری")
    })
    public ResponseEntity<ProductResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                ProductResponse.from(productService.update(id, request, me)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_DELETE')")
    @Operation(summary = "غیرفعال‌سازی محصول",
               description = "حذف واقعی نمی‌شود چون سفارش‌های قدیمی به آن ارجاع دارند",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        productService.deactivate(id, me);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "فعال‌سازی مجدد محصول",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProductResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(ProductResponse.from(productService.activate(id, me)));
    }

    // ==================== تصاویر ====================

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "افزودن تصویر",
               description = "حداکثر ۳ تصویر، هر کدام تا ۲ مگابایت، "
                           + "با فرمت JPEG، PNG یا WebP. اولین تصویر خودکار اصلی می‌شود",
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
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
    @Operation(summary = "حذف تصویر",
               description = "اگر تصویر اصلی حذف شود، اولین تصویر باقی‌مانده جایگزین می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long id,
            @PathVariable Long imageId,
            @AuthenticationPrincipal UserPrincipal me) {

        productService.deleteImage(id, imageId, me);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("hasAuthority('PRODUCT_UPDATE')")
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