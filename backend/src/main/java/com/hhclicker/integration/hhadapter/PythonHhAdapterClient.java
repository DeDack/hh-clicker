package com.hhclicker.integration.hhadapter;

import com.hhclicker.exception.BusinessException;
import com.hhclicker.integration.hhadapter.dto.request.ApplyAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.GenerateCoverLetterAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.HhSessionAdapterPayload;
import com.hhclicker.integration.hhadapter.dto.request.LoadResumeAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.LoadVacancyAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.ParseCurlAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.SearchVacanciesAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.request.SessionAdapterRequest;
import com.hhclicker.integration.hhadapter.dto.response.AdapterErrorResponse;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;
import java.util.function.Supplier;

@Component
public class PythonHhAdapterClient implements HhAdapterClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(PythonHhAdapterClient.class);
    private static final int SAFE_RETRIES = 2;

    private final RestClient restClient;
    private final HhAdapterProperties properties;
    private final HhAdapterExceptionMapper exceptionMapper;

    public PythonHhAdapterClient(RestClient hhAdapterRestClient, HhAdapterProperties properties, HhAdapterExceptionMapper exceptionMapper) {
        this.restClient = hhAdapterRestClient;
        this.properties = properties;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public ParsedCurlAdapterResponse parseCurl(String rawCurl) {
        return retrySafe("parseCurl", () -> post("/internal/v1/curl/parse", new ParseCurlAdapterRequest(rawCurl), ParsedCurlAdapterResponse.class));
    }

    @Override
    public SessionValidationAdapterResponse validateSession(HhSessionAdapterPayload session) {
        return retrySafe("validateSession", () -> post("/internal/v1/hh/session/validate", new SessionAdapterRequest(session), SessionValidationAdapterResponse.class));
    }

    @Override
    public ListResumesAdapterResponse listResumes(HhSessionAdapterPayload session) {
        return retrySafe("listResumes", () -> post("/internal/v1/hh/resumes/list", new SessionAdapterRequest(session), ListResumesAdapterResponse.class));
    }

    @Override
    public LoadResumeAdapterResponse loadResume(HhSessionAdapterPayload session, String resumeId, String title) {
        return retrySafe("loadResume", () -> post("/internal/v1/hh/resumes/load", new LoadResumeAdapterRequest(session, resumeId, title), LoadResumeAdapterResponse.class));
    }

    @Override
    public VacancySearchAdapterResponse searchVacancies(HhSessionAdapterPayload session, String searchUrl, int pages, String resumeId) {
        return retrySafe("searchVacancies", () -> post("/internal/v1/hh/vacancies/search", new SearchVacanciesAdapterRequest(session, searchUrl, pages, resumeId), VacancySearchAdapterResponse.class));
    }

    @Override
    public LoadVacancyAdapterResponse loadVacancy(HhSessionAdapterPayload session, String vacancyId, String title) {
        return retrySafe("loadVacancy", () -> post("/internal/v1/hh/vacancies/load", new LoadVacancyAdapterRequest(session, vacancyId, title), LoadVacancyAdapterResponse.class));
    }

    @Override
    public GeneratedCoverLetterAdapterResponse generateCoverLetter(GenerateCoverLetterAdapterRequest request) {
        return post("/internal/v1/cover-letters/generate", request, GeneratedCoverLetterAdapterResponse.class);
    }

    @Override
    public ApplyAdapterResponse apply(ApplyAdapterRequest request) {
        return post("/internal/v1/hh/applications/apply", request, ApplyAdapterResponse.class);
    }

    @Override
    public AdapterStatusResponse getLlmStatus() {
        return retrySafe("getLlmStatus", () -> get("/internal/v1/llm/status", AdapterStatusResponse.class));
    }

    @Override
    public AdapterHealthResponse getHealth() {
        return retrySafe("getHealth", () -> restClient.get().uri("/health").retrieve().body(AdapterHealthResponse.class));
    }

    private <T> T retrySafe(String operation, Supplier<T> supplier) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= SAFE_RETRIES + 1; attempt++) {
            try {
                return supplier.get();
            } catch (BusinessException ex) {
                last = ex;
                break;
            } catch (RestClientException ex) {
                last = ex;
                LOGGER.warn("hh_adapter operation={} attempt={} failed", operation, attempt);
            }
        }
        throw last == null ? new BusinessException("ADAPTER_UNAVAILABLE", "Python adapter недоступен") : last;
    }

    private <T> T get(String path, Class<T> responseType) {
        try {
            return restClient.get()
                .uri(path)
                .header("X-Internal-Api-Key", properties.getApiKey())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .retrieve()
                .body(responseType);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new BusinessException("ADAPTER_UNAVAILABLE", "Python adapter недоступен");
        }
    }

    private <T> T post(String path, Object request, Class<T> responseType) {
        try {
            return restClient.post()
                .uri(path)
                .header("X-Internal-Api-Key", properties.getApiKey())
                .header("X-Request-Id", UUID.randomUUID().toString())
                .body(request)
                .retrieve()
                .body(responseType);
        } catch (RestClientResponseException ex) {
            throw mapResponseException(ex);
        } catch (RestClientException ex) {
            throw new BusinessException("ADAPTER_UNAVAILABLE", "Python adapter недоступен");
        }
    }

    private BusinessException mapResponseException(RestClientResponseException ex) {
        AdapterErrorResponse error = null;
        try {
            error = ex.getResponseBodyAs(AdapterErrorResponse.class);
        } catch (Exception ignored) {
            // Response body can be non-JSON; keep public error safe.
        }
        return exceptionMapper.map(ex, error);
    }
}
