package com.codebreak.backend.websocket;

import com.codebreak.backend.auth.JwtService;
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

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

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

        if (!StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

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

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        username,
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        accessor.setUser(authentication);

        return message;
    }
}
