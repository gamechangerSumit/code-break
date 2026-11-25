package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.workspace.RedisWorkspaceService;
import com.codebreak.backend.workspace.WorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;

import static org.mockito.Mockito.*;

class CollaborationControllerTests {

    private SimpMessagingTemplate messagingTemplate;
    private ProjectMemberService projectMemberService;
    private RedisWorkspaceService redisWorkspaceService;
    private WorkspaceService workspaceService;
    private CollaborationController controller;
    private final Principal alice = () -> "alice";

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        projectMemberService = mock(ProjectMemberService.class);
        redisWorkspaceService = mock(RedisWorkspaceService.class);
        workspaceService = mock(WorkspaceService.class);
        controller = new CollaborationController(
                messagingTemplate,
                projectMemberService,
                redisWorkspaceService,
                workspaceService
        );
    }

    @Test
    void ignoresWorkspaceTraversalPathBeforeAccessingWorkspaceServices() {
        CollaborationMessage message = new CollaborationMessage();
        message.setProjectId(42L);
        message.setFilePath("../secrets.txt");
        message.setContent("malicious");

        controller.edit(message, alice);

        verifyNoInteractions(projectMemberService);
        verifyNoInteractions(workspaceService);
        verifyNoInteractions(redisWorkspaceService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ignoresOverlappingEditBatchWithoutMutatingDocumentOrBroadcasting() {
        when(workspaceService.getFileContent(42L, "src/App.tsx", "alice"))
                .thenReturn("hello");

        CollaborationMessage message = new CollaborationMessage();
        message.setProjectId(42L);
        message.setFilePath("src/App.tsx");
        message.setChanges(List.of(
                new CollaborationMessage.CodeChange(1, 3, "x"),
                new CollaborationMessage.CodeChange(2, 1, "y")
        ));

        controller.edit(message, alice);

        verify(projectMemberService).requireWriteAccess(42L, "alice");
        verify(workspaceService).getFileContent(42L, "src/App.tsx", "alice");
        verifyNoInteractions(redisWorkspaceService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ignoresIncompleteEditBatchWithoutMutatingDocumentOrBroadcasting() {
        when(workspaceService.getFileContent(42L, "src/App.tsx", "alice"))
                .thenReturn("hello");

        CollaborationMessage message = new CollaborationMessage();
        message.setProjectId(42L);
        message.setFilePath("src/App.tsx");
        message.setChanges(java.util.Arrays.asList(
                new CollaborationMessage.CodeChange(null, 0, "x")
        ));

        controller.edit(message, alice);

        verify(projectMemberService).requireWriteAccess(42L, "alice");
        verifyNoInteractions(redisWorkspaceService);
        verifyNoInteractions(messagingTemplate);
    }
}
