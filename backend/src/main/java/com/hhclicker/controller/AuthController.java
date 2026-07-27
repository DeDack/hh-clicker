package com.hhclicker.controller;

import com.hhclicker.config.JwtProperties;
import com.hhclicker.dto.request.LoginRequest;
import com.hhclicker.dto.request.RefreshTokenRequest;
import com.hhclicker.dto.request.RegisterRequest;
import com.hhclicker.dto.response.AuthResponse;
import com.hhclicker.dto.response.CurrentUserResponse;
import com.hhclicker.exception.BusinessException;
import com.hhclicker.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthService authService, JwtProperties jwtProperties) {
        this.authService = authService;
        this.jwtProperties = jwtProperties;
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        AuthService.TokenPair tokens = authService.register(request);
        setRefreshCookie(response, tokens.refreshToken());
        return tokens.response();
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.TokenPair tokens = authService.login(request);
        setRefreshCookie(response, tokens.refreshToken());
        return tokens.response();
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(
        @RequestBody(required = false) RefreshTokenRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        String refreshToken = request != null && request.refreshToken() != null
            ? request.refreshToken()
            : readRefreshCookie(httpRequest);
        AuthService.TokenPair tokens = authService.refresh(refreshToken);
        setRefreshCookie(response, tokens.refreshToken());
        return tokens.response();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @RequestBody(required = false) RefreshTokenRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse response
    ) {
        String refreshToken = request != null && request.refreshToken() != null
            ? request.refreshToken()
            : readRefreshCookie(httpRequest);
        authService.logout(refreshToken);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BusinessException("UNAUTHORIZED", "Требуется авторизация");
        }
        return authService.me(authentication.getName());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new BusinessException("UNAUTHORIZED", "Refresh token отсутствует");
        }
        return Arrays.stream(cookies)
            .filter(cookie -> jwtProperties.getRefreshCookieName().equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Refresh token отсутствует"));
    }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getRefreshCookieName(), token)
            .httpOnly(true)
            .secure(jwtProperties.isRefreshCookieSecure())
            .sameSite(jwtProperties.getRefreshCookieSameSite())
            .path("/api/auth")
            .maxAge(Duration.ofDays(jwtProperties.getRefreshTokenTtlDays()))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(jwtProperties.getRefreshCookieName(), "")
            .httpOnly(true)
            .secure(jwtProperties.isRefreshCookieSecure())
            .sameSite(jwtProperties.getRefreshCookieSameSite())
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
