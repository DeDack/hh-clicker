package com.hhclicker.dto.response;

import com.hhclicker.entity.ApplicationAttempt;

import java.time.Instant;
import java.util.UUID;

public record ApplicationAttemptResponse(
    UUID id,
    int attemptNumber,
    String status,
    Integer httpStatus,
    String errorCode,
    String safeErrorMessage,
    String topicId,
    Instant createdAt
) {
    public static ApplicationAttemptResponse from(ApplicationAttempt attempt) {
        return new ApplicationAttemptResponse(
            attempt.getId(),
            attempt.getAttemptNumber(),
            attempt.getStatus().name(),
            attempt.getHttpStatus(),
            attempt.getErrorCode(),
            attempt.getSafeErrorMessage(),
            attempt.getTopicId(),
            attempt.getCreatedAt()
        );
    }
}
