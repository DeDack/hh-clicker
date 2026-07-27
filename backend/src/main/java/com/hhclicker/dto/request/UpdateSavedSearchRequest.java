package com.hhclicker.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateSavedSearchRequest(
    @NotNull UUID hhAccountId,
    @NotNull UUID resumeId,
    @NotBlank @Size(max = 200) String name,
    @NotBlank String searchUrl,
    @Min(1) @Max(50) int pages,
    @Min(0) Integer vacancyLoadLimit,
    String includeKeywords,
    String excludeKeywords,
    String defaultCoverLetterMode,
    String defaultCommonCoverLetter,
    BigDecimal defaultDelaySeconds,
    @Min(0) int defaultMaxApplications,
    boolean active
) {
}
