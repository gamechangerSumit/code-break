package com.codebreak.backend.auth;

public record LoginRequest(
        String username,
        String password
) {
}