package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;

class WorkspacePersistenceSchedulerTests {

    @Test
    void retriesDirtyFileAfterMinioWriteFailsAndClearsOnlyAfterSuccessfulSave() {
        RedisWorkspaceService redis = mock(RedisWorkspaceService.class);
        MinioWorkspaceService minio = mock(MinioWorkspaceService.class);
        WorkspaceFileMetadataRepository repository = mock(WorkspaceFileMetadataRepository.class);
        WorkspaceFileMetadata metadata = mock(WorkspaceFileMetadata.class);

        WorkspacePersistenceScheduler scheduler =
                new WorkspacePersistenceScheduler(redis, minio, repository);

        when(redis.getActiveProjects()).thenReturn(Set.of("7"));
        when(redis.getDirtyFiles(7L)).thenReturn(Set.of("src/Main.java"));
        when(redis.getDirtyContent(7L, "src/Main.java")).thenReturn("class Main {}");
        when(repository.findByProjectIdAndPath(7L, "src/Main.java"))
                .thenReturn(Optional.of(metadata));
        when(metadata.getVersion()).thenReturn(3L);
        doThrow(new IllegalStateException("MinIO unavailable"))
                .doNothing()
                .when(minio).saveFile(7L, "src/Main.java", "class Main {}");
        when(redis.compareAndClearDirty(7L, "src/Main.java", "class Main {}"))
                .thenReturn(true);

        scheduler.flushDirtyFiles();
        verify(redis, never()).compareAndClearDirty(7L, "src/Main.java", "class Main {}");
        verify(repository, never()).save(metadata);

        scheduler.flushDirtyFiles();

        verify(minio, times(2)).saveFile(7L, "src/Main.java", "class Main {}");
        verify(repository, times(1)).save(metadata);
        verify(redis, times(1)).compareAndClearDirty(7L, "src/Main.java", "class Main {}");
        verify(metadata).setSize("class Main {}".getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        verify(metadata).setVersion(4L);
    }

    @Test
    void keepsDirtyContentWhenFileMetadataIsMissing() {
        RedisWorkspaceService redis = mock(RedisWorkspaceService.class);
        MinioWorkspaceService minio = mock(MinioWorkspaceService.class);
        WorkspaceFileMetadataRepository repository = mock(WorkspaceFileMetadataRepository.class);

        WorkspacePersistenceScheduler scheduler =
                new WorkspacePersistenceScheduler(redis, minio, repository);

        when(redis.getActiveProjects()).thenReturn(Set.of("7"));
        when(redis.getDirtyFiles(7L)).thenReturn(Set.of("src/New.java"));
        when(redis.getDirtyContent(7L, "src/New.java")).thenReturn("class New {}");
        when(repository.findByProjectIdAndPath(7L, "src/New.java"))
                .thenReturn(Optional.empty());

        scheduler.flushDirtyFiles();

        verify(minio, never()).saveFile(anyLong(), anyString(), anyString());
        verify(repository, never()).save(any());
        verify(redis, never()).clearDirty(7L, "src/New.java");
        verify(redis, never()).compareAndClearDirty(7L, "src/New.java", "class New {}");
    }

    @Test
    void ignoresMalformedProjectIdsInActiveProjectIndex() {
        RedisWorkspaceService redis = mock(RedisWorkspaceService.class);
        MinioWorkspaceService minio = mock(MinioWorkspaceService.class);
        WorkspaceFileMetadataRepository repository = mock(WorkspaceFileMetadataRepository.class);

        WorkspacePersistenceScheduler scheduler =
                new WorkspacePersistenceScheduler(redis, minio, repository);

        when(redis.getActiveProjects()).thenReturn(Set.of("not-a-project", "-"));

        scheduler.flushDirtyFiles();

        verify(redis, never()).getDirtyFiles(anyLong());
        verifyNoInteractions(minio, repository);
    }
}
