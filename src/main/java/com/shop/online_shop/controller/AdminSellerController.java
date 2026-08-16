package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.RejectRequest;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.SellerResponse;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.SellerService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
@Tag(name = "Management — Sellers",
     description = "بررسی و مدیریت فروشندگان — مدیر و ادمین")
public class AdminSellerController {

    private final SellerService sellerService;

    @GetMapping
    @PreAuthorize("hasAuthority('SELLER_REVIEW')")
    @Operation(summary = "لیست فروشندگان",
               description = "فیلتر بر اساس وضعیت: PENDING، ACTIVE، REJECTED، SUSPENDED",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<SellerResponse>> list(
            @Parameter(description = "وضعیت حساب فروشنده")
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = sellerService.list(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, SellerResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SELLER_REVIEW')")
    @Operation(summary = "جزئیات فروشنده",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<SellerResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(SellerResponse.from(sellerService.getById(id)));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('SELLER_REVIEW')")
    @Operation(summary = "تأیید فروشنده",
               description = "پس از تأیید، فروشنده می‌تواند وارد شود و محصول ثبت کند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "تأیید شد"),
        @ApiResponse(responseCode = "400", description = "قبلاً تأیید شده"),
        @ApiResponse(responseCode = "403", description = "مجوز ندارید")
    })
    public ResponseEntity<SellerResponse> approve(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(SellerResponse.from(sellerService.approve(id, me.getId())));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('SELLER_REVIEW')")
    @Operation(summary = "رد درخواست فروشنده",
               description = "فروشنده می‌تواند بعداً دوباره درخواست دهد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "رد شد"),
        @ApiResponse(responseCode = "400", description = "فروشنده فعال است — از تعلیق استفاده کنید")
    })
    public ResponseEntity<SellerResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                SellerResponse.from(sellerService.reject(id, request.reason(), me.getId())));
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('SELLER_REVIEW')")
    @Operation(summary = "تعلیق فروشنده",
               description = "محصولات فعالش غیرفعال و علامت‌گذاری می‌شوند، "
                           + "و نشست‌های فعالش بسته می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<SellerResponse> suspend(
            @PathVariable Long id,
            @Valid @RequestBody RejectRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                SellerResponse.from(sellerService.suspend(id, request.reason(), me.getId())));
    }

    @PatchMapping("/{id}/unsuspend")
    @PreAuthorize("hasAuthority('SELLER_REVIEW')")
    @Operation(summary = "رفع تعلیق فروشنده",
               description = "فقط محصولاتی که به دلیل تعلیق غیرفعال شده بودند برمی‌گردند؛ "
                           + "محصولاتی که خود فروشنده غیرفعال کرده بود دست‌نخورده می‌مانند",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<SellerResponse> unsuspend(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(SellerResponse.from(sellerService.unsuspend(id, me.getId())));
    }
}