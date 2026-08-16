package com.shop.online_shop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Service
@Slf4j
public class JwtService {

    private final Key signingKey;
    private final SecureRandom random = new SecureRandom();

    @Getter private final long accessExpirationMs;
    @Getter private final long refreshExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long accessExpirationMs,
            @Value("${app.jwt.refresh-expiration}") long refreshExpirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UserPrincipal principal) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(principal.getEmail())
                .addClaims(Map.of(
                        "uid", principal.getId(),
                        "role", principal.getRoleName()))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /** رشته تصادفی امن — نه JWT، چون فقط در دیتابیس اعتبارسنجی می‌شود */
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String extractEmailIfValid(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return null;
        }
    }

    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }
}