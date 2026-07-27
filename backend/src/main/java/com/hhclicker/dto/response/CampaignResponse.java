package com.hhclicker.dto.response;

import com.hhclicker.entity.ApplicationCampaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CampaignResponse(
    UUID id,
    UUID hhAccountId,
    String hhAccountName,
    UUID resumeId,
    String resumeName,
    UUID savedSearchId,
    String savedSearchName,
    String name,
    String searchUrl,
    int pages,
    Integer vacancyLoadLimit,
    String includeKeywords,
    String excludeKeywords,
    String status,
    String coverLetterMode,
    String commonCoverLetter,
    boolean reviewCoverLettersBeforeApply,
    BigDecimal delaySeconds,
    int maxApplications,
    boolean stopRequested,
    int totalVacancies,
    int generatedCount,
    int appliedCount,
    int alreadyCount,
    int skippedCount,
    int failedCount,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    Instant updatedAt
) {
    public static CampaignResponse from(ApplicationCampaign campaign) {
        return new CampaignResponse(
            campaign.getId(),
            campaign.getHhAccount().getId(),
            campaign.getHhAccount().getName(),
            campaign.getResume().getId(),
            campaign.getResume().getTitle(),
            campaign.getSavedSearch() == null ? null : campaign.getSavedSearch().getId(),
            campaign.getSavedSearch() == null ? null : campaign.getSavedSearch().getName(),
            campaign.getName(),
            campaign.getSearchUrl(),
            campaign.getPages(),
            campaign.getVacancyLoadLimit(),
            campaign.getIncludeKeywords(),
            campaign.getExcludeKeywords(),
            campaign.getStatus().name(),
            campaign.getCoverLetterMode(),
            campaign.getCommonCoverLetter(),
            campaign.isReviewCoverLettersBeforeApply(),
            campaign.getDelaySeconds(),
            campaign.getMaxApplications(),
            campaign.isStopRequested(),
            campaign.getTotalVacancies(),
            campaign.getGeneratedCount(),
            campaign.getAppliedCount(),
            campaign.getAlreadyCount(),
            campaign.getSkippedCount(),
            campaign.getFailedCount(),
            campaign.getCreatedAt(),
            campaign.getStartedAt(),
            campaign.getFinishedAt(),
            campaign.getUpdatedAt()
        );
    }
}
