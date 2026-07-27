package com.hhclicker.integration.hhadapter;

import com.hhclicker.integration.hhadapter.dto.request.ApplyAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.GenerateCoverLetterAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.response.AdapterHealthResponse;
import com.hhclicker.integration.hhadapter.dto.response.AdapterStatusResponse;
import com.hhclicker.integration.hhadapter.dto.response.ApplyAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.GeneratedCoverLetterAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.ListResumesAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.LoadResumeAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.LoadVacancyAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.ParsedCurlAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.SessionValidationAdapterResponse;
import com.hhclicker.integration.hhadapter.dto.response.VacancySearchAdapterResponse;

public interface HhAdapterClient {
    ParsedCurlAdapterResponse parseCurl(String rawCurl);
    SessionValidationAdapterResponse validateSession(HhSessionAdapterPayload session);
    ListResumesAdapterResponse listResumes(HhSessionAdapterPayload session);
    LoadResumeAdapterResponse loadResume(HhSessionAdapterPayload session, String resumeId, String title);
    VacancySearchAdapterResponse searchVacancies(HhSessionAdapterPayload session, String searchUrl, int pages, String resumeId);
    LoadVacancyAdapterResponse loadVacancy(HhSessionAdapterPayload session, String vacancyId, String title);
    GeneratedCoverLetterAdapterResponse generateCoverLetter(GenerateCoverLetterAdapterRequest request);
    ApplyAdapterResponse apply(ApplyAdapterRequest request);
    AdapterStatusResponse getLlmStatus();
    AdapterHealthResponse getHealth();
}
