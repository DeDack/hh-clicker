package com.hhclicker.integration.hhadapter.dto.response;

import java.util.Map;

public record GeneratedCoverLetterAdapterResponse(
    String status,
    String coverLetter,
    Map<String, Object> matchAnalysis,
    String provider,
    String model,
    String promptVersion,
    int inputTokens,
    int outputTokens,
    int attempts,
    String errorCode
) {
}
