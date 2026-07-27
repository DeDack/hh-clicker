package com.hhclicker.integration.hhadapter.dto.request;

public record ApplyAdapterRequest(HhSessionAdapterPayload session, String resumeId, String vacancyId, String coverLetter) {
}
