package com.hhclicker.integration.hhadapter.dto.response;

import java.util.List;

public record LoadVacancyAdapterResponse(
    String hhVacancyId,
    String title,
    String companyName,
    String url,
    String description,
    String descriptionHash,
    List<String> questions
) {
}
