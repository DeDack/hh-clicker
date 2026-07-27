package com.hhclicker.integration.hhadapter.dto.response;

import java.util.Map;

public record ParsedCurlAdapterResponse(
    String url,
    Map<String, String> cookies,
    Map<String, String> headers,
    int cookiesCount,
    boolean hasHhToken,
    boolean hasXsrf
) {
}
