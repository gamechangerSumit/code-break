package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class PresenceController {

    static final int MAX_TRACKED_SESSIONS = 10_000;
    private static final String[] COLORS = {
            "#60a5fa", "#f472b6", "#34d399", "#fbbf24",
            "#a78bfa", "#fb923c", "#22d3ee", "#f87171"
    };

    private final SimpMessagingTemplate messagingTemplate;
    private final ProjectMemberService projectMemberService;

    // A user may have multiple browser tabs. Track each STOMP session,
    // then collapse sessions by username when publishing the room snapshot.
    private final Map<String, PresenceState> sessions = new ConcurrentHashMap<>();
    private final Object sessionAdmissionLock = new Object();

    @MessageMapping("/presence/join")
    public void join(ProjectRequest request, Principal principal,
                     SimpMessageHeaderAccessor headers) {
        if (request == null || request.projectId() == null || principal == null) {
            return;
        }
        projectMemberService.requireReadAccess(request.projectId(), principal.getName());
        String sessionId = headers.getSessionId();
        if (sessionId == null) {
            return;
        }
        PresenceState previous;
        synchronized (sessionAdmissionLock) {
            previous = sessions.get(sessionId);
            if (previous == null && sessions.size() >= MAX_TRACKED_SESSIONS) {
                return;
            }
            sessions.put(sessionId, new PresenceState(
                    request.projectId(), principal.getName(), null,
                    1, 1, 1, 1, 1, 1, colorFor(principal.getName())
            ));
        }
        if (previous != null && !previous.projectId().equals(request.projectId())) {
            broadcast(previous.projectId());
        }
        broadcast(request.projectId());
    }

    @MessageMapping("/cursor")
    public void cursor(CursorUpdate update, Principal principal,
                      SimpMessageHeaderAccessor headers) {
        if (update == null || update.projectId() == null || principal == null) {
            return;
        }
        String filePath = update.filePath() == null ? "" : update.filePath().trim();
        if (filePath.isEmpty() || filePath.length() > 512) {
            return;
        }
        projectMemberService.requireReadAccess(update.projectId(), principal.getName());
        String sessionId = headers.getSessionId();
        if (sessionId == null) {
            return;
        }

        PresenceState previous = sessions.get(sessionId);
        if (previous == null
                || !previous.projectId().equals(update.projectId())
                || !previous.username().equals(principal.getName())) {
            return;
        }

        PresenceState next = new PresenceState(
                update.projectId(), principal.getName(), filePath,
                clamp(update.lineNumber()), clamp(update.column()),
                clamp(update.selectionStartLine()), clamp(update.selectionStartColumn()),
                clamp(update.selectionEndLine()), clamp(update.selectionEndColumn()),
                colorFor(principal.getName())
        );
        sessions.put(sessionId, next);
        broadcast(update.projectId());
    }

    @MessageMapping("/presence/leave")
    public void leave(ProjectRequest request, SimpMessageHeaderAccessor headers) {
        String sessionId = headers.getSessionId();
        if (sessionId == null) {
            return;
        }
        PresenceState removed = sessions.remove(sessionId);
        if (removed != null) {
            broadcast(removed.projectId());
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        PresenceState removed = sessions.remove(event.getSessionId());
        if (removed != null) {
            broadcast(removed.projectId());
        }
    }

    private void broadcast(Long projectId) {
        Map<String, PresenceState> uniqueUsers = sessions.values().stream()
                .filter(state -> state.projectId().equals(projectId))
                .collect(Collectors.toMap(
                        PresenceState::username,
                        state -> state,
                        (first, latest) -> latest
                ));

        List<PresenceUser> users = uniqueUsers.values().stream()
                .sorted(Comparator.comparing(PresenceState::username,
                        String.CASE_INSENSITIVE_ORDER))
                .map(state -> new PresenceUser(
                        state.username(), state.filePath(), state.lineNumber(),
                        state.column(), state.selectionStartLine(),
                        state.selectionStartColumn(), state.selectionEndLine(),
                        state.selectionEndColumn(), state.color()
                ))
                .toList();

        messagingTemplate.convertAndSend(
                "/topic/project/" + projectId + "/presence",
                new PresenceSnapshot(users)
        );
    }

    private int clamp(Integer value) {
        return value == null ? 1 : Math.max(1, Math.min(value, 1_000_000));
    }

    private String colorFor(String username) {
        return COLORS[Math.floorMod(username.hashCode(), COLORS.length)];
    }

    public record ProjectRequest(Long projectId) {}
    public record CursorUpdate(Long projectId, String filePath, Integer lineNumber,
                               Integer column, Integer selectionStartLine,
                               Integer selectionStartColumn, Integer selectionEndLine,
                               Integer selectionEndColumn) {}
    public record PresenceSnapshot(List<PresenceUser> users) {}
    public record PresenceUser(String username, String filePath, int lineNumber,
                               int column, int selectionStartLine,
                               int selectionStartColumn, int selectionEndLine,
                               int selectionEndColumn, String color) {}
    private record PresenceState(Long projectId, String username, String filePath,
                                  int lineNumber, int column,
                                  int selectionStartLine, int selectionStartColumn,
                                  int selectionEndLine, int selectionEndColumn,
                                  String color) {}
}
