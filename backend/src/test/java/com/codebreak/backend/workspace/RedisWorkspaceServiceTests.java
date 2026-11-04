package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RedisWorkspaceServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void clearsCorruptLegacyDirtyPathsWithoutNormalizingThem() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("codebreak:workspace:7:dirty-files"))
                .thenReturn(Set.of("../outside.txt", "src//broken.java"));

        RedisWorkspaceService service = new RedisWorkspaceService(redisTemplate);

        service.clear(7L);

        verify(redisTemplate).delete("codebreak:workspace:7:dirty:../outside.txt");
        verify(redisTemplate).delete("codebreak:workspace:7:dirty:src//broken.java");
        verify(redisTemplate).delete("codebreak:workspace:7:dirty-files");
        verify(redisTemplate).delete("codebreak:workspace:7:active");
        verify(setOperations).remove("codebreak:workspace:active-projects", "7");
    }

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
