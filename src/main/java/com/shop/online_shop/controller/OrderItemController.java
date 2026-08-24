package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.CancelRequest;
import com.shop.online_shop.dto.request.UpdateItemStatusRequest;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.SellerOrderItemResponse;
import com.shop.online_shop.entity.OrderItemStatus;
import com.shop.online_shop.security.Scope;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/order-items")
@RequiredArgsConstructor
@Tag(name = "Order Items", description = "اقلام سفارش — انجام سفارش توسط فروشنده")
public class OrderItemController {

    private final OrderService orderService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ORDER_FULFILL', 'ORDER_READ_ALL')")
    @Operation(summary = "فهرست اقلام سفارش",
               description = "mine اقلام مربوط به محصولات خودم که نیازمند ORDER_FULFILL است، "
                           + "all اقلام همه فروشندگان که نیازمند ORDER_READ_ALL است",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "موفق"),
        @ApiResponse(responseCode = "403", description = "مجوز لازم برای این دامنه را ندارید")
    })
    public ResponseEntity<PagedResponse<SellerOrderItemResponse>> list(
            @Parameter(description = "mine | all")
            @RequestParam(required = false) String scope,

            @Parameter(description = "فیلتر وضعیت قلم")
            @RequestParam(required = false) OrderItemStatus status,

            @Parameter(description = "فیلتر فروشنده — تنها در دامنه all")
            @RequestParam(required = false) Long sellerId,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,

            @AuthenticationPrincipal UserPrincipal me) {

        var result = orderService.listItems(Scope.parse(scope), status, sellerId, me,
                PageRequest.of(page, size, Sort.by("id").descending()));

        return ResponseEntity.ok(
                PagedResponse.from(result, SellerOrderItemResponse::from));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('ORDER_FULFILL', 'ORDER_UPDATE')")
    @Operation(summary = "تغییر وضعیت یک قلم",
               description = "چرخه مجاز: PAID سپس PROCESSING سپس SHIPPED سپس DELIVERED. "
                           + "پرش از مراحل یا بازگشت به عقب مجاز نیست. فروشنده تنها اقلام "
                           + "خودش را و دارنده ORDER_UPDATE همه را تغییر می‌دهد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "به‌روز شد"),
        @ApiResponse(responseCode = "400", description = "انتقال وضعیت نامعتبر"),
        @ApiResponse(responseCode = "404", description = "این قلم متعلق به شما نیست")
    })
    public ResponseEntity<SellerOrderItemResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateItemStatusRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var item = orderService.updateItemStatus(id, request.status(), me);
        return ResponseEntity.ok(SellerOrderItemResponse.from(item));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('ORDER_FULFILL', 'ORDER_UPDATE')")
    @Operation(summary = "لغو یک قلم",
               description = "مثلاً وقتی کالا آسیب دیده است. موجودی بازمی‌گردد و "
                           + "مبلغ سفارش بازمحاسبه می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<SellerOrderItemResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var item = orderService.cancelItem(id, request.reason(), me);
        return ResponseEntity.ok(SellerOrderItemResponse.from(item));
    }
}