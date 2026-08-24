package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "پرداخت‌ها")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_READ_ALL')")
    @Operation(summary = "فهرست همه پرداخت‌ها",
               description = "پرداخت یک سفارش مشخص از مسیر /orders/{id}/payment "
                           + "قابل دریافت است",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = paymentService.getAllPayments(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("orderId", p.getOrder().getId());
            row.put("customer", p.getOrder().getUser().getFullName());
            row.put("amount", p.getAmount());
            row.put("status", p.getStatus().name());
            row.put("transactionRef", p.getTransactionRef());
            row.put("paidAt", p.getPaidAt());
            row.put("createdAt", p.getCreatedAt());
            return row;
        }));
    }
}