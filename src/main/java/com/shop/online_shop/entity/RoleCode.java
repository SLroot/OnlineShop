package com.shop.online_shop.entity;

/**
 * نقش‌های پایه که جریان‌های ثابت سامانه به آن‌ها وابسته‌اند.
 *
 * این تنها جایی است که کد برنامه به یک نقش مشخص ارجاع می‌دهد.
 * هر تصمیم دیگری درباره دسترسی باید بر اساس مجوز گرفته شود، نه نام نقش،
 * تا نقش‌های سفارشی هم بدون تغییر کد کار کنند.
 */
public final class RoleCode {

    /** مشتری — تنها نقشی که در ثبت‌نام آزاد و بدون تأیید ساخته می‌شود */
    public static final String USER = "USER";

    /** فروشنده — ثبت‌نام آزاد ولی نیازمند تأیید مدیر */
    public static final String SELLER = "SELLER";

    /** مدیر — توسط ادمین ساخته می‌شود */
    public static final String MANAGER = "MANAGER";

    /** ادمین کل — در راه‌اندازی اولیه ساخته می‌شود */
    public static final String ADMIN = "ADMIN";

    private RoleCode() {
    }
}