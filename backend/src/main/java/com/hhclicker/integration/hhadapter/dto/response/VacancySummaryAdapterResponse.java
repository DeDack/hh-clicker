package com.hhclicker.integration.hhadapter.dto.response;

public record VacancySummaryAdapterResponse(
    String hhVacancyId,
    String url,
    String title,
    String searchText,
    int sourcePage,
    boolean alreadyApplied,
    String applicationState,
    String applicationStateSource
) {
}
