package com.shop.online_shop.service;

import com.shop.online_shop.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * محدودیت تلاش ورود در حافظه.
 * برای محیط چند-نمونه‌ای باید به Redis منتقل شود.
 */
@Service
@Slf4j
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private record Attempt(int count, Instant blockedUntil) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void assertNotBlocked(String email) {
        Attempt attempt = attempts.get(email);
        if (attempt == null || attempt.blockedUntil() == null) return;

        if (attempt.blockedUntil().isAfter(Instant.now())) {
            long minutes = Duration.between(Instant.now(), attempt.blockedUntil()).toMinutes() + 1;
            throw ApiException.tooManyRequests(
                    "تعداد تلاش‌های ناموفق زیاد است. " + minutes + " دقیقه دیگر تلاش کنید");
        }
        attempts.remove(email);
    }

    public void recordFailure(String email) {
        attempts.compute(email, (key, existing) -> {
            int count = (existing == null ? 0 : existing.count()) + 1;

            if (count >= MAX_ATTEMPTS) {
                log.warn("Login blocked for {} after {} failed attempts", email, count);
                return new Attempt(count, Instant.now().plus(BLOCK_DURATION));
            }
            return new Attempt(count, null);
        });
    }

    public void recordSuccess(String email) {
        attempts.remove(email);
    }
}
