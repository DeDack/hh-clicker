package com.hhclicker.dto.request;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record UpdateCampaignSettingsRequest(
    String coverLetterMode,
    String commonCoverLetter,
    Boolean reviewCoverLettersBeforeApply,
    BigDecimal delaySeconds,
    @Min(0) Integer maxApplications
) {
}
