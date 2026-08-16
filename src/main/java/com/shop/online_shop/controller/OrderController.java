package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.CancelRequest;
import com.shop.online_shop.dto.request.PaymentRequest;
import com.shop.online_shop.dto.request.PlaceOrderRequest;
import com.shop.online_shop.dto.response.OrderResponse;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.entity.Payment;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.OrderService;
import com.shop.online_shop.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "سفارش‌ها و پرداخت — مشتری")
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    // ==================== سفارش ====================

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "ثبت سفارش از سبد خرید",
               description = "کل سبد به سفارش تبدیل می‌شود. اقلامی که ناموجود یا "
                           + "غیرفعال شده‌اند نادیده گرفته می‌شوند و توضیحشان در "
                           + "notices می‌آید. موجودی همین لحظه کسر می‌شود و "
                           + "۲۰ دقیقه مهلت پرداخت دارید",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "سفارش ثبت شد"),
        @ApiResponse(responseCode = "400", description = "سبد خالی یا هیچ قلمی قابل سفارش نبود"),
        @ApiResponse(responseCode = "403", description = "این نقش امکان خرید ندارد"),
        @ApiResponse(responseCode = "404", description = "آدرس یافت نشد")
    })
    public ResponseEntity<Map<String, Object>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var result = orderService.placeOrder(request.addressId(), me);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order", OrderResponse.from(result.order()));
        body.put("notices", result.notices());

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "سفارش‌های من",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<OrderResponse>> myOrders(
            @AuthenticationPrincipal UserPrincipal me,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        var result = orderService.getMyOrders(me.getId(),
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, OrderResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "جزئیات سفارش",
               description = "شامل وضعیت تک‌تک اقلام و اطلاعات پرداخت",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "404", description = "سفارش یافت نشد یا متعلق به شما نیست")
    public ResponseEntity<OrderResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(OrderResponse.from(orderService.getOrder(id, me)));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER_READ')")
    @Operation(summary = "لغو سفارش",
               description = "تا قبل از ارسال امکان‌پذیر است. موجودی محصولات برمی‌گردد "
                           + "و در صورت پرداخت، مبلغ بازپرداخت می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "لغو شد"),
        @ApiResponse(responseCode = "400", description = "سفارش ارسال شده یا قبلاً لغو شده")
    })
    public ResponseEntity<OrderResponse> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var order = orderService.cancelOrder(id, request.reason(), me);

        // بازپرداخت اینجا هماهنگ می‌شود تا OrderService به PaymentService وابسته نشود
        paymentService.refundIfPaid(order, me.getId());

        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // ==================== پرداخت ====================

    @PostMapping("/{id}/payment")
    @PreAuthorize("hasAuthority('ORDER_CREATE')")
    @Operation(summary = "پرداخت سفارش — شبیه‌ساز",
               description = "درگاه واقعی نیست. با simulateSuccess=false می‌توانید "
                           + "مسیر پرداخت ناموفق را هم تست کنید. "
                           + "پس از پرداخت موفق، همه اقلام به وضعیت PAID می‌روند",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "پرداخت موفق"),
        @ApiResponse(responseCode = "400",
                     description = "پرداخت ناموفق، مهلت تمام‌شده، یا قبلاً پرداخت شده")
    })
    public ResponseEntity<Map<String, Object>> pay(
            @PathVariable Long id,
            @RequestBody(required = false) PaymentRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        boolean success = request == null || request.success();
        Payment payment = paymentService.pay(id, success, me);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", payment.getStatus().name());
        body.put("amount", payment.getAmount());
        body.put("transactionRef", payment.getTransactionRef());
        body.put("paidAt", payment.getPaidAt());

        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}/payment")
    @PreAuthorize("hasAuthority('PAYMENT_READ')")
    @Operation(summary = "وضعیت پرداخت سفارش",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, Object>> getPayment(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        Payment p = paymentService.getByOrder(id, me);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", p.getStatus().name());
        body.put("amount", p.getAmount());
        body.put("transactionRef", p.getTransactionRef());
        body.put("failureReason", p.getFailureReason());
        body.put("paidAt", p.getPaidAt());
        body.put("refundedAt", p.getRefundedAt());

        return ResponseEntity.ok(body);
    }
}