package com.hhclicker.integration.hhadapter.dto.response;

import java.util.List;

public record SessionValidationAdapterResponse(
    boolean valid,
    String status,
    String message,
    List<ResumeSummaryAdapterResponse> resumes
) {
}
