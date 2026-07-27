package com.hhclicker.dto.response;

import com.hhclicker.entity.Resume;

import java.time.Instant;
import java.util.UUID;

public record ResumeResponse(
    UUID id,
    UUID hhAccountId,
    String hhAccountName,
    String hhResumeId,
    String title,
    String text,
    String contentHash,
    String candidateProfile,
    String telegramUsername,
    String gender,
    boolean active,
    Instant lastSyncedAt
) {
    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
            resume.getId(),
            resume.getHhAccount().getId(),
            resume.getHhAccount().getName(),
            resume.getHhResumeId(),
            resume.getTitle(),
            resume.getText(),
            resume.getContentHash(),
            resume.getCandidateProfile(),
            resume.getTelegramUsername(),
            resume.getGender().name(),
            resume.isActive(),
            resume.getLastSyncedAt()
        );
    }
}
