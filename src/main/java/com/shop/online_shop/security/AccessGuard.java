package com.shop.online_shop.security;

import com.shop.online_shop.exception.ApiException;
import org.springframework.stereotype.Component;

@Component
public class AccessGuard {

    /**
     * منابع عمومی مانند محصول — وجودشان راز نیست، پس ۴۰۳ می‌دهیم.
     */
    public void assertOwnerOrPrivileged(Long ownerId, UserPrincipal me,
                                        String privilegedAuthority, String message) {
        if (me != null && ownerId.equals(me.getId())) {
            return;
        }
        if (me != null && me.hasAuthority(privilegedAuthority)) {
            return;
        }
        throw ApiException.forbidden(message);
    }

    /**
     * منابع خصوصی مانند سفارش و آدرس — ۴۰۴ می‌دهیم تا وجود رکورد لو نرود.
     */
    public void assertOwnerOrPrivilegedPrivate(Long ownerId, UserPrincipal me,
                                               String privilegedAuthority, String message) {
        if (me != null && ownerId.equals(me.getId())) {
            return;
        }
        if (me != null && me.hasAuthority(privilegedAuthority)) {
            return;
        }
        throw ApiException.notFound(message);
    }

    /**
     * بررسی مجوز لازم برای یک دامنه دید.
     * پیام خطا نام مجوز را می‌گوید تا پنل مدیریت بتواند آن را نمایش دهد.
     */
    public void requireAuthority(UserPrincipal me, String authority) {
        if (me == null) {
            throw ApiException.unauthorized("برای این بخش باید وارد شوید");
        }
        if (!me.hasAuthority(authority)) {
            throw ApiException.forbidden(
                    "برای این عملیات به مجوز " + authority + " نیاز دارید");
        }
    }

    public boolean has(UserPrincipal me, String authority) {
        return me != null && me.hasAuthority(authority);
    }
}