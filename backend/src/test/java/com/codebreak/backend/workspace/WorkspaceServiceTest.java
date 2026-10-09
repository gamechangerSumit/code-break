package com.codebreak.backend.workspace;

import com.codebreak.backend.project.ProjectMemberService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class WorkspaceServiceTest {

    @Test
    void cleanupKeepsMetadataWhenMinioDeletionFails() {
        WorkspaceFileMetadataRepository fileRepository = mock(WorkspaceFileMetadataRepository.class);
        WorkspaceFolderMetadataRepository folderRepository = mock(WorkspaceFolderMetadataRepository.class);
        MinioWorkspaceService minioService = mock(MinioWorkspaceService.class);
        RedisWorkspaceService redisService = mock(RedisWorkspaceService.class);
        ProjectMemberService memberService = mock(ProjectMemberService.class);

        WorkspaceFileMetadata file = WorkspaceFileMetadata.builder()
                .path("src/Main.java")
                .build();
        when(fileRepository.findByProjectIdOrderByPathAsc(7L)).thenReturn(List.of(file));
        doThrow(new IllegalStateException("MinIO unavailable"))
                .when(minioService).deleteFile(7L, "src/Main.java");

        WorkspaceService service = new WorkspaceService(
                fileRepository,
                folderRepository,
                minioService,
                redisService,
                memberService
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.clearProjectWorkspace(7L)
        );

        verify(fileRepository, never()).deleteByProjectId(eq(7L));
        verify(folderRepository, never()).deleteByProjectId(eq(7L));
    }
}
