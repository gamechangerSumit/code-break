package com.codebreak.backend.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String description
) {
}
