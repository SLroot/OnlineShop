package com.shop.online_shop.repository;

import com.shop.online_shop.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** توکن‌های قبلی کاربر را باطل می‌کند تا فقط آخرین توکن معتبر باشد */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now " +
           "WHERE t.user.id = :userId AND t.usedAt IS NULL")
    int invalidateAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** شمارش درخواست‌های اخیر — برای جلوگیری از spam */
    @Query("SELECT COUNT(t) FROM PasswordResetToken t " +
           "WHERE t.user.id = :userId AND t.createdAt > :since")
    long countRecentRequests(@Param("userId") Long userId, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
