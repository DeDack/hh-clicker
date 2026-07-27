package com.hhclicker.integration.hhadapter.dto.request;

public record LoadVacancyAdapterRequest(HhSessionAdapterPayload session, String vacancyId, String title) {
}
