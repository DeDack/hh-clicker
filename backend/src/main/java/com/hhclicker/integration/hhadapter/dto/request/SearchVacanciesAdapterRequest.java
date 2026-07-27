package com.hhclicker.integration.hhadapter.dto.request;

public record SearchVacanciesAdapterRequest(HhSessionAdapterPayload session, String searchUrl, int pages, String resumeId) {
}
