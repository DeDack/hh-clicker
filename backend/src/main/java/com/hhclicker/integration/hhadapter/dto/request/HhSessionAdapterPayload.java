package com.hhclicker.integration.hhadapter.dto.request;

import java.util.Map;

public record HhSessionAdapterPayload(Map<String, String> cookies, Map<String, String> headers) {
}
