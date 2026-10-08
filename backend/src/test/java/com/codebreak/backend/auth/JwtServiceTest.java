package com.codebreak.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "test-secret-that-is-at-least-32-bytes-long-123456";

    @Test
    void generatedTokenContainsUsername() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", SECRET);

        service.initialize();

        String token = service.generateToken("alice");

        assertNotNull(token);
        assertEquals("alice", service.extractUsername(token));
    }

    @Test
    void initializeRejectsMissingSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                service::initialize
        );

        assertEquals(
                "JWT_SECRET must be configured",
                exception.getMessage()
        );
    }

    @Test
    void initializeRejectsShortSecret() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "too-short");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                service::initialize
        );

        assertEquals(
                "JWT_SECRET must contain at least 32 bytes",
                exception.getMessage()
        );
    }

    @Test
    void extractUsernameRejectsTamperedToken() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", SECRET);
        service.initialize();

        String token = service.generateToken("alice");
        String tampered = token.substring(0, token.length() - 1) + "x";

        assertThrows(
                RuntimeException.class,
                () -> service.extractUsername(tampered)
        );
    }
}
