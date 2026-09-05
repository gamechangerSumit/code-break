package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import org.springframework.stereotype.Controller;

import java.security.Principal;


@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ProjectMemberService projectMemberService;

    private final SimpMessagingTemplate messagingTemplate;


    // =========================================
    // SEND CHAT MESSAGE
    // =========================================

    @MessageMapping("/chat")
    public void sendMessage(

            ChatMessage message,

            Principal principal

    ) {

        // =====================================
        // VALIDATION
        // =====================================

        if (
                message == null ||
                        principal == null ||
                        message.projectId() == null ||
                        message.content() == null ||
                        message.content().isBlank()
        ) {

            return;
        }


        // =====================================
        // AUTHENTICATED USER
        // =====================================

        String username =
                principal.getName();


        // =====================================
        // PROJECT ACCESS
        // =====================================

        projectMemberService.requireReadAccess(

                message.projectId(),

                username

        );


        // =====================================
        // CLEAN MESSAGE
        // =====================================

        ChatMessage cleanMessage =
                new ChatMessage(

                        message.projectId(),

                        username,

                        message.content().trim()

                );


        // =====================================
        // REALTIME BROADCAST
        // =====================================

        messagingTemplate.convertAndSend(

                "/topic/project/"
                        + message.projectId()
                        + "/chat",

                cleanMessage

        );

    }
}