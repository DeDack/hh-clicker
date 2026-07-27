package com.hhclicker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCampaignRequest(
    UUID hhAccountId,
    UUID resumeId,
    UUID savedSearchId,
    @NotBlank @Size(max = 220) String name,
    String searchUrl,
    @Min(1) @Max(50) Integer pages,
    @Min(0) Integer vacancyLoadLimit,
    String includeKeywords,
    String excludeKeywords,
    String coverLetterMode,
    String commonCoverLetter,
    Boolean reviewCoverLettersBeforeApply,
    BigDecimal delaySeconds,
    @Min(0) Integer maxApplications,
    Overrides overrides
) {
    public record Overrides(
        Integer pages,
        Integer maxApplications,
        BigDecimal delaySeconds,
        Integer vacancyLoadLimit,
        String coverLetterMode,
        String commonCoverLetter,
        Boolean reviewCoverLettersBeforeApply,
        String includeKeywords,
        String excludeKeywords
    ) {
    }
}
