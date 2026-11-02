package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.mockito.Mockito.*;

class PresenceControllerTests {

    private SimpMessagingTemplate messagingTemplate;
    private ProjectMemberService projectMemberService;
    private PresenceController controller;
    private Principal alice;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        projectMemberService = mock(ProjectMemberService.class);
        controller = new PresenceController(messagingTemplate, projectMemberService);
        alice = () -> "alice";
    }

    @Test
    void ignoresCursorUpdatesFromSessionsThatHaveNotJoined() {
        SimpMessageHeaderAccessor headers = headers("session-1");

        controller.cursor(
                new PresenceController.CursorUpdate(42L, "src/App.tsx", 10, 2, 10, 2, 10, 2),
                alice,
                headers
        );

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void broadcastsCursorUpdatesForJoinedSession() {
        SimpMessageHeaderAccessor headers = headers("session-1");
        controller.join(new PresenceController.ProjectRequest(42L), alice, headers);

        controller.cursor(
                new PresenceController.CursorUpdate(42L, "src/App.tsx", 10, 2, 10, 2, 10, 2),
                alice,
                headers
        );

        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/project/42/presence"),
                any(PresenceController.PresenceSnapshot.class)
        );
    }

    @Test
    void ignoresCursorUpdatesWhenSessionBelongsToAnotherProject() {
        SimpMessageHeaderAccessor headers = headers("session-1");
        controller.join(new PresenceController.ProjectRequest(42L), alice, headers);

        controller.cursor(
                new PresenceController.CursorUpdate(43L, "src/App.tsx", 10, 2, 10, 2, 10, 2),
                alice,
                headers
        );

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/project/42/presence"),
                any(PresenceController.PresenceSnapshot.class)
        );
    }

    private SimpMessageHeaderAccessor headers(String sessionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId(sessionId);
        return headers;
    }
}
