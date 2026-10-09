package com.codebreak.backend.file;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectImportRequest(
        @NotEmpty
        @Size(max = 500)
        List<@Valid @NotNull ProjectImportFile> files,

        @Size(max = 500)
        List<@NotNull @Size(max = 1000) String> folders
) {
}