package com.hhclicker.integration.hhadapter.dto.request;

public record LoadResumeAdapterRequest(HhSessionAdapterPayload session, String resumeId, String title) {
}
