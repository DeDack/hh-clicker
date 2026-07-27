package com.hhclicker.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefreshHhSessionRequest(@NotBlank String rawCurl) {
}
