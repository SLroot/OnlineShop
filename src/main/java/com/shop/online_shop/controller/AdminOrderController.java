package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.OrderResponse;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.entity.OrderStatus;
import com.shop.online_shop.service.OrderService;
import com.shop.online_shop.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Management — Orders", description = "مدیریت سفارش‌ها و پرداخت‌ها")
public class AdminOrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('ORDER_READ_ALL')")
    @Operation(summary = "همه سفارش‌ها",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<OrderResponse>> orders(
            @Parameter(description = "فیلتر وضعیت سفارش")
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = orderService.getAllOrders(status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, OrderResponse::withCustomer));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAuthority('PAYMENT_READ_ALL')")
    @Operation(summary = "همه پرداخت‌ها",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<Map<String, Object>>> payments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = paymentService.getAllPayments(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, p -> Map.of(
                "id", p.getId(),
                "orderId", p.getOrder().getId(),
                "customer", p.getOrder().getUser().getFullName(),
                "amount", p.getAmount(),
                "status", p.getStatus().name(),
                "transactionRef", p.getTransactionRef() == null ? "" : p.getTransactionRef(),
                "createdAt", p.getCreatedAt())));
    }
}