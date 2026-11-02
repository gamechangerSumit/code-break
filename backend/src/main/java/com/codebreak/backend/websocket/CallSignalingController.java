package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class CallSignalingController {
    private final ProjectMemberService members;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/call.signal")
    public void signal(CallSignal signal, Principal principal) {
        if (signal == null || principal == null || signal.projectId() == null ||
                signal.type() == null || signal.type().isBlank()) return;
        String username = principal.getName();
        members.requireReadAccess(signal.projectId(), username);
        CallSignal outgoing = new CallSignal(signal.projectId(), signal.type(), username,
                signal.toUsername(), signal.sdp(), signal.candidate(), signal.sdpMid(),
                signal.sdpMLineIndex());
        if (signal.toUsername() != null && !signal.toUsername().isBlank()) {
            messagingTemplate.convertAndSendToUser(signal.toUsername(), "/queue/calls", outgoing);
        } else {
            messagingTemplate.convertAndSend("/topic/project/" + signal.projectId() + "/calls", outgoing);
        }
    }

    public record CallSignal(Long projectId, String type, String fromUsername, String toUsername,
                             String sdp, String candidate, String sdpMid, Integer sdpMLineIndex) {}
}
