package com.codebreak.backend.file;

public record ProjectFileRequest(
        String name,
        String path,
        String content
) {
}