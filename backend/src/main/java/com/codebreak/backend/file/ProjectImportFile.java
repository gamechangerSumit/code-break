package com.codebreak.backend.file;

import jakarta.validation.constraints.Size;

public record ProjectImportFile(
        @Size(max = 255)
        String name,

        @Size(max = 1000)
        String path,

        @Size(max = 5 * 1024 * 1024)
        String content
) {
}