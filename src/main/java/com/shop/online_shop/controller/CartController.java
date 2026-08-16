package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.AddCartItemRequest;
import com.shop.online_shop.dto.request.UpdateCartItemRequest;
import com.shop.online_shop.dto.response.CartResponse;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/carts/me")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CART_MANAGE')")
@Tag(name = "Shopping Cart", description = "سبد خرید کاربر جاری")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "مشاهده سبد خرید",
               description = "قیمت‌ها همیشه از محصول خوانده می‌شوند. "
                           + "اقلام غیرفعال یا ناموجود حذف و اقلام مازاد بر موجودی "
                           + "کاهش می‌یابند؛ توضیح این تغییرات در فیلد notices می‌آید",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "موفق"),
        @ApiResponse(responseCode = "403", description = "این نقش امکان خرید ندارد")
    })
    public ResponseEntity<CartResponse> myCart(
            @AuthenticationPrincipal UserPrincipal me) {
        return ResponseEntity.ok(cartService.getMyCart(me.getId()));
    }

    @PostMapping("/items")
    @Operation(summary = "افزودن به سبد",
               description = "اگر محصول از قبل در سبد باشد، تعداد جمع می‌شود. "
                           + "تعداد نهایی از موجودی محصول بیشتر نخواهد شد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "افزوده شد"),
        @ApiResponse(responseCode = "400", description = "محصول ناموجود، غیرفعال، یا متعلق به خودتان"),
        @ApiResponse(responseCode = "404", description = "محصول یافت نشد")
    })
    public ResponseEntity<CartResponse> addItem(
            @AuthenticationPrincipal UserPrincipal me,
            @Valid @RequestBody AddCartItemRequest request) {

        return ResponseEntity.ok(
                cartService.addItem(me.getId(), request.productId(), request.quantity()));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "تغییر تعداد یک قلم",
               description = "تعداد جایگزین می‌شود، نه اضافه",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "به‌روز شد"),
        @ApiResponse(responseCode = "404", description = "این قلم در سبد شما نیست")
    })
    public ResponseEntity<CartResponse> updateItem(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemRequest request) {

        return ResponseEntity.ok(
                cartService.updateQuantity(me.getId(), itemId, request.quantity()));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "حذف یک قلم از سبد",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CartResponse> removeItem(
            @AuthenticationPrincipal UserPrincipal me,
            @PathVariable Long itemId) {

        return ResponseEntity.ok(cartService.removeItem(me.getId(), itemId));
    }

    @DeleteMapping("/items")
    @Operation(summary = "خالی کردن سبد",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<CartResponse> clear(
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(cartService.clear(me.getId()));
    }
}