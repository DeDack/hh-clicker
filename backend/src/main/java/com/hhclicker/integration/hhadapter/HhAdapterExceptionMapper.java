package com.hhclicker.integration.hhadapter;

import com.hhclicker.exception.BusinessException;
import com.hhclicker.integration.hhadapter.dto.response.AdapterErrorResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HhAdapterExceptionMapper {
    public BusinessException map(RestClientResponseException ex, AdapterErrorResponse error) {
        String adapterCode = error == null ? "" : error.code();
        String code = switch (adapterCode) {
            case "LLM_NOT_CONFIGURED", "LLM_UNAUTHORIZED", "LLM_RATE_LIMITED", "LLM_TIMEOUT", "LLM_BAD_RESPONSE",
                 "PROFILE_MISMATCH" -> adapterCode;
            case "UNAUTHORIZED" -> "ADAPTER_UNAVAILABLE";
            default -> isAuthStatus(ex.getStatusCode()) ? "ADAPTER_UNAVAILABLE" : "ADAPTER_UNAVAILABLE";
        };
        String message = adapterCode == null || adapterCode.isBlank()
            ? "Python adapter недоступен или вернул ошибку"
            : "Python adapter вернул ошибку: " + adapterCode;
        return new BusinessException(code, message);
    }

    private boolean isAuthStatus(HttpStatusCode status) {
        return status.value() == 401 || status.value() == 403;
    }
}
