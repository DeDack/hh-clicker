package com.hhclicker.dto.response;

public record AuthResponse(String accessToken, long accessTokenExpiresInSeconds, CurrentUserResponse user) {
}
