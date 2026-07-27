package com.hhclicker.dto.response;

import com.hhclicker.entity.User;

import java.util.UUID;

public record CurrentUserResponse(UUID id, String email, String role, String status, UserFeatureResponse features) {
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getStatus().name(),
            new UserFeatureResponse(user.isCoverLetterGenerationEnabled())
        );
    }
}
