package com.shop.online_shop.service;

import com.shop.online_shop.entity.PasswordResetToken;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.PasswordResetTokenRepository;
import com.shop.online_shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration RATE_WINDOW = Duration.ofHours(1);
    private static final int MAX_REQUESTS_PER_WINDOW = 3;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random = new SecureRandom();

    /**
     * درخواست بازیابی.
     * چه ایمیل وجود داشته باشد چه نه، خطایی برنمی‌گردد —
     * وگرنه می‌شود فهمید چه ایمیل‌هایی در سیستم ثبت هستند.
     */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmail(email.toLowerCase().trim())
                .filter(User::canLogin)
                .ifPresent(this::createAndSendToken);
    }

    private void createAndSendToken(User user) {
        long recent = tokenRepository.countRecentRequests(
                user.getId(), Instant.now().minus(RATE_WINDOW));

        if (recent >= MAX_REQUESTS_PER_WINDOW) {
            log.warn("Password reset rate limit hit for user {}", user.getId());
            return;   // سکوت — به کاربر چیزی نمی‌گوییم
        }

        tokenRepository.invalidateAllForUser(user.getId(), Instant.now());

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .user(user)
                .expiresAt(Instant.now().plus(TOKEN_TTL))
                .build());

        auditLogService.record(user.getId(), "PASSWORD_RESET_REQUESTED", null);

        // در محیط واقعی اینجا ایمیل ارسال می‌شود
        log.info("=== PASSWORD RESET TOKEN for {} ===", user.getEmail());
        log.info("=== {} ===", token);
        log.info("=== expires in {} minutes ===", TOKEN_TTL.toMinutes());
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken stored = tokenRepository.findByToken(rawToken)
                .orElseThrow(() -> ApiException.badRequest("توکن نامعتبر است"));

        if (!stored.isUsable()) {
            throw ApiException.badRequest("توکن منقضی شده یا قبلاً استفاده شده است");
        }

        User user = stored.getUser();

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw ApiException.badRequest("رمز جدید نباید با رمز فعلی یکسان باشد");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        stored.setUsedAt(Instant.now());
        tokenRepository.save(stored);

        // همه نشست‌های فعال باطل می‌شوند
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.record(user.getId(), "PASSWORD_RESET_COMPLETED", null);
        log.info("Password reset completed for user {}", user.getId());
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void cleanupExpired() {
        int deleted = tokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) log.info("Deleted {} expired reset tokens", deleted);
    }
}