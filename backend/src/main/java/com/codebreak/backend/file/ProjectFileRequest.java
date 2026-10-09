package com.codebreak.backend.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProjectFileRequest(
        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 1000)
        String path,

        @Size(max = 5 * 1024 * 1024)
        String content
) {
}