package com.hhclicker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHhAccountRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank String rawCurl
) {
}
