package com.shop.online_shop.controller;

import com.shop.online_shop.dto.request.ChangeRoleRequest;
import com.shop.online_shop.dto.request.CreateUserRequest;
import com.shop.online_shop.dto.response.PagedResponse;
import com.shop.online_shop.dto.response.UserResponse;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.security.UserPrincipal;
import com.shop.online_shop.service.UserService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "مدیریت کاربران و نقش آن‌ها")
public class UserController {

    private final UserService userService;

    // ==================== خواندن ====================

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "فهرست کاربران",
               description = "قابل فیلتر بر اساس نقش و وضعیت حساب",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<PagedResponse<UserResponse>> list(
            @Parameter(description = "فیلتر بر اساس شناسه نقش")
            @RequestParam(required = false) Long roleId,

            @Parameter(description = "PENDING | ACTIVE | REJECTED | SUSPENDED")
            @RequestParam(required = false) UserStatus status,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var result = userService.list(roleId, status,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return ResponseEntity.ok(PagedResponse.from(result, UserResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "جزئیات یک کاربر",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(id)));
    }

    // ==================== ساخت ====================

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    @Operation(summary = "ساخت کاربر با نقش دلخواه",
               description = "رمز اولیه را به کاربر تحویل دهید؛ در نخستین ورود موظف به "
                           + "تغییر آن است. نمی‌توانید نقشی بدهید که مجوزهایش فراتر از "
                           + "دسترسی خودتان باشد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "کاربر ساخته شد"),
        @ApiResponse(responseCode = "403", description = "نقش انتخابی فراتر از دسترسی شماست"),
        @ApiResponse(responseCode = "409", description = "ایمیل تکراری")
    })
    public ResponseEntity<UserResponse> create(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        var user = userService.create(request, me);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    // ==================== نقش و وضعیت ====================

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "تغییر نقش کاربر",
               description = "نشست‌های کاربر باطل می‌شود تا مجوزهای تازه اعمال گردد",
               security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "نقش تغییر کرد"),
        @ApiResponse(responseCode = "400", description = "تغییر نقش خود یا آخرین ادمین"),
        @ApiResponse(responseCode = "403", description = "نقش انتخابی فراتر از دسترسی شماست")
    })
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(
                UserResponse.from(userService.changeRole(id, request.roleId(), me)));
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "تعلیق حساب کاربر",
               description = "نشست‌های فعال کاربر بسته می‌شود",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserResponse> suspend(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(UserResponse.from(userService.suspend(id, me)));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @Operation(summary = "فعال‌سازی مجدد حساب کاربر",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal me) {

        return ResponseEntity.ok(UserResponse.from(userService.activate(id, me)));
    }
}
