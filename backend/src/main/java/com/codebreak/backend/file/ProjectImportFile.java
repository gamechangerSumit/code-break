package com.codebreak.backend.file;

public record ProjectImportFile(
        String name,
        String path,
        String content
) {
}