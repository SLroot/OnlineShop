package com.shop.online_shop.service;

import com.shop.online_shop.entity.RefreshToken;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.RefreshTokenRepository;
import com.shop.online_shop.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    @Transactional
    public RefreshToken issue(User user) {
        return refreshTokenRepository.save(RefreshToken.builder()
                .token(jwtService.generateRefreshToken())
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMs()))
                .build());
    }

    /**
     * چرخش توکن: توکن قدیمی باطل و توکن جدید صادر می‌شود.
     * اگر توکن باطل‌شده دوباره استفاده شود، همه توکن‌های کاربر باطل می‌شوند
     * چون احتمال سرقت وجود دارد.
     */
    @Transactional
    public RefreshToken rotate(String rawToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(rawToken)
                .orElseThrow(() -> ApiException.unauthorized("refresh token نامعتبر است"));

        if (stored.isRevoked()) {
            log.warn("Reuse of revoked refresh token for user {}", stored.getUser().getId());
            refreshTokenRepository.revokeAllByUserId(stored.getUser().getId());
            throw ApiException.unauthorized("نشست شما باطل شده است، دوباره وارد شوید");
        }

        if (!stored.isUsable()) {
            throw ApiException.unauthorized("refresh token منقضی شده است");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        return issue(stored.getUser());
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        int count = refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Revoked {} refresh tokens for user {}", count, userId);
    }

    /** هر روز ساعت ۳ بامداد توکن‌های منقضی پاک می‌شوند */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpired() {
        int deleted = refreshTokenRepository.deleteExpired(Instant.now());
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }
}