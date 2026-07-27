package com.hhclicker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateHhAccountRequest(@NotBlank @Size(max = 160) String name) {
}
