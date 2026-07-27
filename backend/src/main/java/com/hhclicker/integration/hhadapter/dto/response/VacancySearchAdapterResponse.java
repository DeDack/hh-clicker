package com.hhclicker.integration.hhadapter.dto.response;

import java.util.List;
import java.util.Map;

public record VacancySearchAdapterResponse(List<VacancySummaryAdapterResponse> vacancies, Map<String, Object> diagnostics) {
}
