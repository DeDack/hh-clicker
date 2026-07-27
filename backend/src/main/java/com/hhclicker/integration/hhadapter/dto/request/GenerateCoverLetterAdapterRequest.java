package com.hhclicker.integration.hhadapter.dto.request;

import java.util.List;

public record GenerateCoverLetterAdapterRequest(
    ResumePayload resume,
    String candidateProfile,
    String candidateGender,
    String telegramUsername,
    VacancyPayload vacancy,
    SettingsPayload settings
) {
    public record ResumePayload(String title, String text, String contentHash) {
    }

    public record VacancyPayload(String hhVacancyId, String title, String companyName, String description, List<String> questions) {
    }

    public record SettingsPayload(String style, boolean useCompany, boolean useVacancyTitle, int maxAttempts) {
    }
}
