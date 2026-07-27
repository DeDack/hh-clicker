package com.hhclicker.dto.response;

import com.hhclicker.entity.CampaignVacancy;

import java.time.Instant;
import java.util.UUID;

public record CampaignVacancyResponse(
    UUID id,
    String hhVacancyId,
    String title,
    String companyName,
    String vacancyUrl,
    String description,
    String descriptionHash,
    int sourcePage,
    boolean selected,
    boolean alreadyApplied,
    String coverLetter,
    String coverLetterStatus,
    boolean coverLetterEditedManually,
    String generationProvider,
    String generationModel,
    String promptVersion,
    int inputTokens,
    int outputTokens,
    int generationAttempts,
    String generationError,
    String applyStatus,
    String applyErrorCode,
    Instant createdAt,
    Instant updatedAt
) {
    public static CampaignVacancyResponse from(CampaignVacancy vacancy) {
        return new CampaignVacancyResponse(
            vacancy.getId(),
            vacancy.getHhVacancyId(),
            vacancy.getTitle(),
            vacancy.getCompanyName(),
            vacancy.getVacancyUrl(),
            vacancy.getDescription(),
            vacancy.getDescriptionHash(),
            vacancy.getSourcePage(),
            vacancy.isSelected(),
            vacancy.isAlreadyApplied(),
            vacancy.getCoverLetter(),
            vacancy.getCoverLetterStatus().name(),
            vacancy.isCoverLetterEditedManually(),
            vacancy.getGenerationProvider(),
            vacancy.getGenerationModel(),
            vacancy.getPromptVersion(),
            vacancy.getInputTokens(),
            vacancy.getOutputTokens(),
            vacancy.getGenerationAttempts(),
            vacancy.getGenerationError(),
            vacancy.getApplyStatus().name(),
            vacancy.getApplyErrorCode(),
            vacancy.getCreatedAt(),
            vacancy.getUpdatedAt()
        );
    }
}
