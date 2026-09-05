package com.codebreak.backend.project;

public record ProjectMemberResponse(
        Long id,
        Long userId,
        String username,
        ProjectRole role
) {
}