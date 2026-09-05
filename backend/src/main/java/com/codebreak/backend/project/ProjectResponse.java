package com.codebreak.backend.project;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        String joinCode
) {
}