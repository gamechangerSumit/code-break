package com.codebreak.backend.workspace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspacePersistenceSchedulerTest {

    @Mock
    private RedisWorkspaceService redisWorkspaceService;

    @Mock
    private MinioWorkspaceService minioWorkspaceService;

    @Mock
    private WorkspaceFileMetadataRepository fileMetadataRepository;

    private WorkspacePersistenceScheduler scheduler;

    private WorkspaceFileMetadata metadata;

    @BeforeEach
    void setUp() {
        scheduler = new WorkspacePersistenceScheduler(
                redisWorkspaceService,
                minioWorkspaceService,
                fileMetadataRepository
        );

        metadata = mock(WorkspaceFileMetadata.class);

        when(redisWorkspaceService.getActiveProjects()).thenReturn(Set.of("7"));
        when(redisWorkspaceService.isActive(7L)).thenReturn(true);
        when(redisWorkspaceService.getDirtyFiles(7L)).thenReturn(Set.of("src/Main.java"));
        when(redisWorkspaceService.getDirtyContent(7L, "src/Main.java"))
                .thenReturn("latest content");
        when(fileMetadataRepository.findByProjectIdAndPath(7L, "src/Main.java"))
                .thenReturn(Optional.of(metadata));
        when(metadata.getVersion()).thenReturn(3L);
    }

    @Test
    void persistsSnapshotAndConditionallyClearsMatchingDirtyValue() {
        scheduler.flushDirtyFiles();

        verify(minioWorkspaceService).saveFile(
                7L,
                "src/Main.java",
                "latest content"
        );
        verify(metadata).setVersion(4L);
        verify(fileMetadataRepository).save(metadata);
        verify(redisWorkspaceService).clearDirtyIfUnchanged(
                7L,
                "src/Main.java",
                "latest content"
        );
    }

    @Test
    void retainsDirtyStateWhenMinioPersistenceFails() {
        doThrow(new IllegalStateException("MinIO unavailable"))
                .when(minioWorkspaceService)
                .saveFile(7L, "src/Main.java", "latest content");

        scheduler.flushDirtyFiles();

        verify(fileMetadataRepository, never()).save(any(WorkspaceFileMetadata.class));
        verify(redisWorkspaceService, never()).clearDirtyIfUnchanged(
                anyLong(),
                anyString(),
                anyString()
        );
    }

    @Test
    void retainsDirtyStateWhenMetadataIsMissing() {
        when(fileMetadataRepository.findByProjectIdAndPath(7L, "src/Main.java"))
                .thenReturn(Optional.empty());

        scheduler.flushDirtyFiles();

        verifyNoInteractions(minioWorkspaceService);
        verify(fileMetadataRepository, never()).save(any(WorkspaceFileMetadata.class));
        verify(redisWorkspaceService, never()).clearDirtyIfUnchanged(
                anyLong(),
                anyString(),
                anyString()
        );
    }
}
