package com.shop.online_shop.repository;

import com.shop.online_shop.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /**
     * ابطال همه توکن‌های یک کاربر — هنگام خروج، تغییر رمز،
     * تغییر نقش یا تغییر مجوزهای نقش.
     *
     * clearAutomatically و flushAutomatically لازم‌اند تا نتیجه این
     * به‌روزرسانی بلافاصله در persistence context هم دیده شود؛
     * بدون آن‌ها، توکنی که پیش‌تر در همان تراکنش خوانده شده
     * همچنان معتبر به نظر می‌رسد.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.revoked = true "
         + "WHERE r.user.id = :userId AND r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /** پاکسازی توکن‌های منقضی توسط زمان‌بند */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}