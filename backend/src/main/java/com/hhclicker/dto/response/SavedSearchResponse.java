package com.hhclicker.dto.response;

import com.hhclicker.entity.SavedSearch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SavedSearchResponse(
    UUID id,
    UUID hhAccountId,
    String hhAccountName,
    UUID resumeId,
    String resumeName,
    String name,
    String searchUrl,
    int pages,
    Integer vacancyLoadLimit,
    String includeKeywords,
    String excludeKeywords,
    String defaultCoverLetterMode,
    String defaultCommonCoverLetter,
    BigDecimal defaultDelaySeconds,
    int defaultMaxApplications,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static SavedSearchResponse from(SavedSearch search) {
        return new SavedSearchResponse(
            search.getId(),
            search.getHhAccount().getId(),
            search.getHhAccount().getName(),
            search.getResume().getId(),
            search.getResume().getTitle(),
            search.getName(),
            search.getSearchUrl(),
            search.getPages(),
            search.getVacancyLoadLimit(),
            search.getIncludeKeywords(),
            search.getExcludeKeywords(),
            search.getDefaultCoverLetterMode(),
            search.getDefaultCommonCoverLetter(),
            search.getDefaultDelaySeconds(),
            search.getDefaultMaxApplications(),
            search.isActive(),
            search.getCreatedAt(),
            search.getUpdatedAt()
        );
    }
}
