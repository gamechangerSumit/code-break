package com.codebreak.backend.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinProjectRequest(
        @NotBlank
        @Size(max = 8)
        String joinCode
) {
}
