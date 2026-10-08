package com.codebreak.backend.auth;

public record UserResponse(
        Long id,
        String username,
        String email
) {
}