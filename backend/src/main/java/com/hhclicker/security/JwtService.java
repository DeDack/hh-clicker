package com.hhclicker.security;

import com.hhclicker.config.JwtProperties;
import com.hhclicker.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {
    private final JwtProperties properties;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenTtlSeconds());
        return Jwts.builder()
            .subject(user.getEmail())
            .claims(Map.of("uid", user.getId().toString(), "role", user.getRole().name()))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key(properties.getAccessSecret()))
            .compact();
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parser()
            .verifyWith(key(properties.getAccessSecret()))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long accessTokenTtlSeconds() {
        return properties.getAccessTokenTtlMinutes() * 60;
    }

    public long refreshTokenTtlSeconds() {
        return properties.getRefreshTokenTtlDays() * 24 * 60 * 60;
    }

    public String refreshCookieName() {
        return properties.getRefreshCookieName();
    }

    private SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
