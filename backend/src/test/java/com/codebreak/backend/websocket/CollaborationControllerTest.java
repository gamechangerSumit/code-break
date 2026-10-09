package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.workspace.RedisWorkspaceService;
import com.codebreak.backend.workspace.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CollaborationControllerTest {

    private static final int MAX_FILE_CONTENT_BYTES = 5 * 1024 * 1024;

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final ProjectMemberService projectMemberService = mock(ProjectMemberService.class);
    private final RedisWorkspaceService redisWorkspaceService = mock(RedisWorkspaceService.class);
    private final WorkspaceService workspaceService = mock(WorkspaceService.class);

    private final CollaborationController controller = new CollaborationController(
            messagingTemplate,
            projectMemberService,
            redisWorkspaceService,
            workspaceService
    );

    private final Principal principal = () -> "test-user";

    @Test
    void rejectsOversizedLiveEditBeforeRedisWrite() {
        CollaborationMessage message = new CollaborationMessage();
        message.setProjectId(1L);
        message.setFilePath("src/Large.java");
        message.setContent("x".repeat(MAX_FILE_CONTENT_BYTES + 1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.edit(message, principal)
        );

        assertEquals("Workspace file content exceeds the 5 MiB limit", exception.getMessage());
        verify(redisWorkspaceService, never()).saveDirtyContent(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void rejectsLegacySyncWithTooManyFilesBeforeImport() {
        CollaborationMessage message = new CollaborationMessage();
        message.setProjectId(1L);
        message.setType("WORKSPACE_SYNC");
        message.setFiles(java.util.stream.IntStream.range(0, 501)
                .mapToObj(i -> new CollaborationMessage.WorkspaceFilePayload(
                        "file-" + i + ".txt",
                        "content"
                ))
                .toList());

        org.mockito.Mockito.doNothing()
                .when(projectMemberService).requireWriteAccess(1L, "test-user");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.workspace(message, principal)
        );

        assertEquals("Workspace import exceeds the 500-file limit", exception.getMessage());
        verify(workspaceService, never()).importWorkspace(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void rejectsOversizedPathForLiveEdit() {
        CollaborationMessage message = new CollaborationMessage();
        message.setProjectId(1L);
        message.setFilePath("a".repeat(1001));
        message.setContent("small");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.edit(message, principal)
        );

        assertEquals("Workspace file path is invalid", exception.getMessage());
    }
}
