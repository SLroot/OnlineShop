package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.AddressRequest;
import com.shop.online_shop.dto.response.AddressResponse;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.AddressService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@RequiredArgsConstructor
@Tag(name = "Addresses", description = "آدرس‌های کاربران")
public class AddressController {

    private final AddressService addressService;

    // ==================== آدرس‌های همه ====================
    // پیش از مسیر شناسه‌دار می‌آید تا all به‌عنوان شناسه تفسیر نشود

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADDRESS_READ_ALL')")
    @Operation(summary = "آدرس‌های همه کاربران",
               description = "با پارامتر userId می‌توان روی یک کاربر فیلتر کرد. "
                           + "پاسخ شامل اطلاعات صاحب آدرس است",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<AddressResponse>> listAll(
            @Parameter(description = "فیلتر روی یک کاربر خاص")
            @RequestParam(required = false) Long userId,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = addressService.getAllAddresses(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, AddressResponse::withOwner));
    }

    // ==================== آدرس‌های کاربر جاری ====================

    @GetMapping
    @PreAuthorize("hasAuthority('ADDRESS_MANAGE')")
    @Operation(summary = "آدرس‌های من",
               description = "آدرس پیش‌فرض همیشه نخستین مورد فهرست است",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<List<AddressResponse>> myAddresses(
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(addressService.getMyAddresses(me.getId())
                .stream().map(AddressResponse::from).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADDRESS_MANAGE', 'ADDRESS_READ_ALL')")
    @Operation(summary = "جزئیات یک آدرس",
               description = "آدرس کاربر دیگر یافت نمی‌شود، مگر برای دارنده ADDRESS_READ_ALL",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "404", description = "آدرس یافت نشد یا متعلق به شما نیست")
    public ResponseEntity<AddressResponse> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                AddressResponse.from(addressService.getMyAddress(id, me.getId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADDRESS_MANAGE')")
    @Operation(summary = "افزودن آدرس",
               description = "نخستین آدرس خودکار پیش‌فرض می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "ساخته شد"),
        @ApiResponse(responseCode = "400", description = "خطای اعتبارسنجی")
    })
    public ResponseEntity<AddressResponse> create(
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var created = addressService.create(request, me.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AddressResponse.from(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ADDRESS_MANAGE')")
    @Operation(summary = "ویرایش آدرس",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AddressResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                AddressResponse.from(addressService.update(id, request, me.getId())));
    }

    @PatchMapping("/{id}/default")
    @PreAuthorize("hasAuthority('ADDRESS_MANAGE')")
    @Operation(summary = "تعیین آدرس پیش‌فرض",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<AddressResponse> setDefault(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                AddressResponse.from(addressService.setDefault(id, me.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADDRESS_MANAGE')")
    @Operation(summary = "حذف آدرس",
               description = "اگر آدرس پیش‌فرض حذف شود، آدرس بعدی جایگزین می‌گردد",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        addressService.delete(id, me.getId());
        return ResponseEntity.noContent().build();
    }
}