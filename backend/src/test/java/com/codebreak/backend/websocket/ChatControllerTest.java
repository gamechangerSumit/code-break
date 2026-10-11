package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ProjectMemberService projectMemberService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(projectMemberService, messagingTemplate);
    }

    @Test
    void broadcastsTrimmedMessageWithAuthenticatedUsername() {
        Principal principal = () -> "alice";

        controller.sendMessage(
                new ChatMessage(42L, "spoofed-user", "  hello team  "),
                principal
        );

        verify(projectMemberService).requireReadAccess(42L, "alice");

        ArgumentCaptor<ChatMessage> messageCaptor =
                ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/project/42/chat"),
                messageCaptor.capture()
        );

        assertEquals(
                new ChatMessage(42L, "alice", "hello team"),
                messageCaptor.getValue()
        );
    }

    @Test
    void ignoresMessagesExceedingConfiguredLengthBeforeBroadcast() {
        String oversizedContent = "x".repeat(
                ChatController.MAX_CHAT_MESSAGE_LENGTH + 1
        );

        controller.sendMessage(
                new ChatMessage(42L, "alice", oversizedContent),
                (Principal) () -> "alice"
        );

        verifyNoInteractions(projectMemberService, messagingTemplate);
    }

    @Test
    void ignoresMessageWithoutAuthenticatedPrincipal() {
        controller.sendMessage(
                new ChatMessage(42L, "alice", "hello"),
                null
        );

        verifyNoInteractions(projectMemberService, messagingTemplate);
    }

    @Test
    void ignoresBlankMessage() {
        controller.sendMessage(
                new ChatMessage(42L, "alice", "   "),
                (Principal) () -> "alice"
        );

        verifyNoInteractions(projectMemberService, messagingTemplate);
    }
}
