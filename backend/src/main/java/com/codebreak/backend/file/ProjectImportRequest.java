package com.codebreak.backend.file;

import java.util.List;

public record ProjectImportRequest(
        List<ProjectImportFile> files,
        List<String> folders
) {
}