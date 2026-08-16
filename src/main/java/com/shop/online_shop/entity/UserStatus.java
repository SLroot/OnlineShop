package com.shop.online_shop.entity;

public enum UserStatus {
    /** ثبت‌نام فروشنده انجام شده، منتظر تأیید مدیر */
    PENDING,
    /** فعال و قادر به ورود */
    ACTIVE,
    /** درخواست فروشندگی رد شده */
    REJECTED,
    /** تعلیق‌شده توسط مدیر */
    SUSPENDED;

    public boolean canLogin() {
        return this == ACTIVE;
    }
}