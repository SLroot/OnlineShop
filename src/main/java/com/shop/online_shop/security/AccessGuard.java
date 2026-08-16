package com.shop.online_shop.security;

import com.shop.online_shop.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    /**
     * منابع عمومی مثل محصول — وجودشان راز نیست، پس ۴۰۳ می‌دهیم.
     */
    public void assertOwnerOrPrivileged(Long ownerId, UserPrincipal me,
                                        String privilegedAuthority, String message) {
        if (ownerId.equals(me.getId())) return;
        if (me.hasAuthority(privilegedAuthority)) return;
        throw ApiException.forbidden(message);
    }

    /**
     * منابع خصوصی مثل سفارش و آدرس — ۴۰۴ می‌دهیم تا وجود رکورد لو نرود.
     */
    public void assertOwnerOrPrivilegedPrivate(Long ownerId, UserPrincipal me,
                                               String privilegedAuthority, String message) {
        if (ownerId.equals(me.getId())) return;
        if (me.hasAuthority(privilegedAuthority)) return;
        throw ApiException.notFound(message);
    }
}
