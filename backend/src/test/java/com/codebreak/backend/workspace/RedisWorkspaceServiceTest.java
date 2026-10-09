package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
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
    void clearsPersistedDirtyContentOnlyWhenRedisValueIsUnchanged() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                eq("saved content"),
                eq("src/Main.java")
        )).thenReturn(1L);

        RedisWorkspaceService service = new RedisWorkspaceService(redisTemplate);

        assertTrue(service.clearDirtyIfUnchanged(
                1L,
                "src/Main.java",
                "saved content"
        ));

        verify(redisTemplate).execute(
                any(DefaultRedisScript.class),
                eq(List.of(
                        "codebreak:workspace:1:dirty:src/Main.java",
                        "codebreak:workspace:1:dirty-files"
                )),
                eq("saved content"),
                eq("src/Main.java")
        );
    }

    @Test
    void doesNotClearDirtyContentWithoutExpectedSnapshot() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisWorkspaceService service = new RedisWorkspaceService(redisTemplate);

        assertFalse(service.clearDirtyIfUnchanged(1L, "src/Main.java", null));

        org.mockito.Mockito.verifyNoInteractions(redisTemplate);
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
