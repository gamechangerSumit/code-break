package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class RedisWorkspaceServiceTests {

    @Test
    void rejectsTraversalBeforeWritingDirtyContentToRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisWorkspaceService service = new RedisWorkspaceService(redisTemplate);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDirtyContent(7L, "../outside.txt", "unsafe")
        );

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void rejectsTraversalBeforeReadingDirtyContentFromRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisWorkspaceService service = new RedisWorkspaceService(redisTemplate);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.getDirtyContent(7L, "src/../../outside.txt")
        );

        verifyNoInteractions(redisTemplate);
    }
}
