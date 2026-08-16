package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.CancelRequest;
import com.shop.online_shop.dto.request.UpdateItemStatusRequest;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.SellerOrderItemResponse;
import com.shop.online_shop.entity.OrderItemStatus;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.OrderService;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller/orders")
@RequiredArgsConstructor
@Tag(name = "Seller — Orders", description = "اقلام سفارش مربوط به فروشنده")
public class SellerOrderController {

    private final OrderService orderService;

    @GetMapping("/items")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    @Operation(summary = "اقلام سفارش من",
               description = "فقط اقلامی که محصولشان متعلق به این فروشنده است",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<SellerOrderItemResponse>> myItems(
            @AuthenticationPrincipal UserPrincipal me,
            @Parameter(description = "فیلتر وضعیت")
            @RequestParam(required = false) OrderItemStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = orderService.getSellerItems(me.getId(), status,
                PageRequest.of(page, size, Sort.by("id").descending()));

        return ResponseEntity.ok(
                PagedResponse.from(result, SellerOrderItemResponse::from));
    }

    @PatchMapping("/items/{itemId}/status")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    @Operation(summary = "تغییر وضعیت یک قلم",
               description = "چرخه مجاز: PAID ← PROCESSING ← SHIPPED ← DELIVERED. "
                           + "پرش از مراحل یا بازگشت به عقب مجاز نیست",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "به‌روز شد"),
        @ApiResponse(responseCode = "400", description = "انتقال وضعیت نامعتبر"),
        @ApiResponse(responseCode = "404", description = "این قلم متعلق به شما نیست")
    })
    public ResponseEntity<SellerOrderItemResponse> updateStatus(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateItemStatusRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var item = orderService.updateItemStatus(itemId, request.status(), me);
        return ResponseEntity.ok(SellerOrderItemResponse.from(item));
    }

    @PatchMapping("/items/{itemId}/cancel")
    @PreAuthorize("hasAuthority('ORDER_FULFILL')")
    @Operation(summary = "لغو یک قلم توسط فروشنده",
               description = "مثلاً وقتی کالا آسیب دیده است. موجودی برمی‌گردد "
                           + "و مبلغ سفارش بازمحاسبه می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<SellerOrderItemResponse> cancelItem(
            @PathVariable Long itemId,
            @Valid @RequestBody CancelRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var item = orderService.cancelItem(itemId, request.reason(), me);
        return ResponseEntity.ok(SellerOrderItemResponse.from(item));
    }
}