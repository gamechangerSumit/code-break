package com.codebreak.backend.websocket;

import com.codebreak.backend.auth.JwtService;
import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern PROJECT_TOPIC =
            Pattern.compile(
                    "^/topic/project/(\\d+)/(code|workspace|chat|members)$"
            );

    private final JwtService jwtService;
    private final ProjectMemberService projectMemberService;

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            authenticateConnect(accessor);
            return message;
        }

        if (
                StompCommand.SUBSCRIBE.equals(command) ||
                        StompCommand.SEND.equals(command)
        ) {
            Principal principal = accessor.getUser();

            if (principal == null) {
                throw new MessagingException(
                        "WebSocket authentication required"
                );
            }

            if (StompCommand.SUBSCRIBE.equals(command)) {
                authorizeSubscription(
                        accessor.getDestination(),
                        principal.getName()
                );
            } else {
                authorizeSendDestination(
                        accessor.getDestination()
                );
            }
        }

        return message;
    }

    private void authenticateConnect(
            StompHeaderAccessor accessor
    ) {
        String authorization =
                accessor.getFirstNativeHeader("Authorization");

        if (
                authorization == null ||
                        !authorization.startsWith("Bearer ")
        ) {
            throw new MessagingException(
                    "WebSocket authentication required"
            );
        }

        String token =
                authorization.substring(7).trim();

        if (token.isBlank()) {
            throw new MessagingException(
                    "WebSocket authentication required"
            );
        }

        final String username;

        try {
            username = jwtService.extractUsername(token);
        } catch (RuntimeException exception) {
            throw new MessagingException(
                    "Invalid or expired WebSocket token",
                    exception
            );
        }

        if (username == null || username.isBlank()) {
            throw new MessagingException(
                    "Invalid WebSocket token"
            );
        }

        accessor.setUser(
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                )
        );
    }

    private void authorizeSubscription(
            String destination,
            String username
    ) {
        if (destination == null || destination.isBlank()) {
            throw new MessagingException(
                    "WebSocket subscription destination is required"
            );
        }

        // Private user queues are authorized by the authenticated principal.
        if (destination.startsWith("/user/queue/")) {
            return;
        }

        Matcher matcher =
                PROJECT_TOPIC.matcher(destination);

        if (!matcher.matches()) {
            throw new MessagingException(
                    "WebSocket subscription destination is not allowed"
            );
        }

        Long projectId =
                Long.valueOf(matcher.group(1));

        try {
            projectMemberService.requireReadAccess(
                    projectId,
                    username
            );
        } catch (RuntimeException exception) {
            throw new MessagingException(
                    "You do not have access to this project",
                    exception
            );
        }
    }

    private void authorizeSendDestination(
            String destination
    ) {
        if (
                destination == null ||
                        !destination.startsWith("/app/")
        ) {
            throw new MessagingException(
                    "WebSocket send destination is not allowed"
            );
        }

        if (
                !destination.equals("/app/edit") &&
                        !destination.equals("/app/workspace") &&
                        !destination.equals("/app/workspace/request") &&
                        !destination.equals("/app/chat")
        ) {
            throw new MessagingException(
                    "WebSocket send destination is not allowed"
            );
        }
    }
}
