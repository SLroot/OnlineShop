package com.shop.online_shop.dto.response;

import java.util.List;

/** مجوزهای یک منبع — برای نمایش گروهی در پنل مدیریت نقش */
public record PermissionGroupResponse(
    String resource,
    List<PermissionResponse> permissions
) {}