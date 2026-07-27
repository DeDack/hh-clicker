package com.hhclicker.integration.hhadapter.dto.response;

import java.util.List;

public record AdapterStatusResponse(
    boolean configured,
    String provider,
    boolean reachable,
    String model,
    Boolean modelAvailable,
    List<String> availableModels,
    String errorCode,
    String message
) {
}
