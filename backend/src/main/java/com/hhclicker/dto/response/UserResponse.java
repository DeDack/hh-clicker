package com.hhclicker.dto.response;

import com.hhclicker.dto.response.UserFeatureResponse;
import com.hhclicker.entity.User;

import java.util.UUID;

public record UserResponse(UUID id, String email, String role, String status, UserFeatureResponse features) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getRole().name(),
            user.getStatus().name(),
            new UserFeatureResponse(user.isCoverLetterGenerationEnabled())
        );
    }
}
