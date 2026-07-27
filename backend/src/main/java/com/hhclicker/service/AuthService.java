package com.hhclicker.service;

import com.hhclicker.dto.request.LoginRequest;
import com.hhclicker.dto.request.RegisterRequest;
import com.hhclicker.dto.response.AuthResponse;
import com.hhclicker.dto.response.CurrentUserResponse;
import com.hhclicker.entity.RefreshToken;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.UserRole;
import com.hhclicker.enumeration.UserStatus;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.repository.RefreshTokenRepository;
import com.hhclicker.repository.UserRepository;
import com.hhclicker.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public TokenPair register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw new BusinessException("CONFLICT", "Пользователь с таким email уже существует");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setCoverLetterGenerationEnabled(false);
        user = users.save(user);
        return issueTokens(user);
    }

    @Transactional
    public TokenPair login(LoginRequest request) {
        User user = users.findByEmail(normalizeEmail(request.email()))
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Неверный email или пароль"));
        ensureActive(user);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("UNAUTHORIZED", "Неверный email или пароль");
        }
        return issueTokens(user);
    }

    @Transactional
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken))
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Refresh token недействителен"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("UNAUTHORIZED", "Refresh token недействителен");
        }
        User user = token.getUser();
        ensureActive(user);
        token.setRevoked(true);
        refreshTokens.save(token);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokens.save(token);
        });
    }

    public CurrentUserResponse me(String email) {
        User user = users.findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Пользователь не найден"));
        ensureActive(user);
        return CurrentUserResponse.from(user);
    }

    private TokenPair issueTokens(User user) {
        String refresh = UUID.randomUUID() + "." + UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(refresh));
        token.setRevoked(false);
        token.setExpiresAt(Instant.now().plusSeconds(jwtService.refreshTokenTtlSeconds()));
        refreshTokens.save(token);
        return new TokenPair(
            new AuthResponse(jwtService.createAccessToken(user), jwtService.accessTokenTtlSeconds(), CurrentUserResponse.from(user)),
            refresh
        );
    }

    private void ensureActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException("FORBIDDEN", "Пользователь заблокирован");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase();
    }

    private String hash(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("UNAUTHORIZED", "Refresh token отсутствует");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record TokenPair(AuthResponse response, String refreshToken) {
    }
}
