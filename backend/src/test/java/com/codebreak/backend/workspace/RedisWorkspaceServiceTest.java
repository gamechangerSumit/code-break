package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class RedisWorkspaceServiceTest {

    private static final int MAX_FILE_CONTENT_BYTES = 5 * 1024 * 1024;

    @Test
    void rejectsOversizedDirtyContentBeforeRedisWrite() {
        RedisWorkspaceService service =
                new RedisWorkspaceService(mock(StringRedisTemplate.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDirtyContent(
                        1L,
                        "src/Large.java",
                        "x".repeat(MAX_FILE_CONTENT_BYTES + 1)
                )
        );

        assertEquals(
                "Workspace file content exceeds the 5 MiB limit",
                exception.getMessage()
        );
    }

    @Test
    void rejectsOversizedDirtyPathBeforeRedisWrite() {
        RedisWorkspaceService service =
                new RedisWorkspaceService(mock(StringRedisTemplate.class));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDirtyContent(
                        1L,
                        "a".repeat(1001),
                        "content"
                )
        );

        assertEquals("Workspace file path is invalid", exception.getMessage());
    }
}
