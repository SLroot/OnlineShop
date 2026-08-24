package com.shop.online_shop.security;

import com.shop.online_shop.exception.ApiException;

/**
 * دامنه دید یک فهرست.
 * جایگزین داشتن مسیرهای جداگانه برای هر نقش؛ مسیر یکی است
 * و کاربر با پارامتر scope مشخص می‌کند چه چیزی می‌خواهد ببیند.
 */
public enum Scope {

    /** فقط داده عمومی — بدون نیاز به توکن */
    PUBLIC,

    /** داده متعلق به خود کاربر */
    MINE,

    /** داده همه کاربران — نیازمند مجوز سراسری */
    ALL;

    public static Scope parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PUBLIC;
        }
        try {
            return Scope.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(
                    "مقدار scope نامعتبر است — مقادیر مجاز: public, mine, all");
        }
    }
}