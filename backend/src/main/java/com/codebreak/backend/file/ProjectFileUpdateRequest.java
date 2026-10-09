package com.codebreak.backend.file;

import jakarta.validation.constraints.Size;

public record ProjectFileUpdateRequest(
        @Size(max = 5 * 1024 * 1024)
        String content
) {
}