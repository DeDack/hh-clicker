package com.hhclicker.dto.response;

import java.util.Map;

public record ErrorResponse(String code, String message, Map<String, Object> details, String requestId) {
}
