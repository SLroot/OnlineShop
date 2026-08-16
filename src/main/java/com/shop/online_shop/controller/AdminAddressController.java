package com.shop.online_shop.controller;

import com.shop.online_shop.dto.response.AddressResponse;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.service.AddressService;
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

@RestController
@RequestMapping("/api/v1/admin/addresses")
@RequiredArgsConstructor
@Tag(name = "Management — Addresses", description = "مشاهده آدرس‌های کاربران")
public class AdminAddressController {

    private final AddressService addressService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADDRESS_READ_ALL')")
    @Operation(summary = "آدرس‌های همه کاربران",
               description = "با پارامتر userId می‌توان روی یک کاربر فیلتر کرد. "
                           + "پاسخ شامل اطلاعات صاحب آدرس است",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<AddressResponse>> list(
            @Parameter(description = "فیلتر روی یک کاربر خاص")
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = addressService.getAllAddresses(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, AddressResponse::withOwner));
    }
}