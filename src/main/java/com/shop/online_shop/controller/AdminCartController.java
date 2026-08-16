package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.CartResponse;
import com.shop.online_shop.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/carts")
@RequiredArgsConstructor
@Tag(name = "Management — Carts", description = "مشاهده سبد خرید کاربران — پشتیبانی")
public class AdminCartController {

    private final CartService cartService;

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('CART_VIEW_ALL')")
    @Operation(summary = "سبد خرید یک کاربر",
               description = "برای پشتیبانی و بررسی مشکلات کاربران. "
                           + "برخلاف مسیر کاربر، سبد اصلاح نمی‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CartResponse> userCart(@PathVariable Long userId) {
        return ResponseEntity.ok(cartService.getUserCart(userId));
    }
}