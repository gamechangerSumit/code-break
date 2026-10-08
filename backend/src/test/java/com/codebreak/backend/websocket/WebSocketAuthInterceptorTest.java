package com.codebreak.backend.websocket;

import com.codebreak.backend.auth.JwtService;
import com.codebreak.backend.project.ProjectMemberService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private ProjectMemberService projectMemberService;

    @Mock
    private MessageChannel channel;

    @Test
    void connectAuthenticatesBearerToken() {
        when(jwtService.extractUsername("token"))
                .thenReturn("alice");

        WebSocketAuthInterceptor interceptor =
                new WebSocketAuthInterceptor(
                        jwtService,
                        projectMemberService
                );

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.setNativeHeader(
                "Authorization",
                "Bearer token"
        );

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        Message<?> result =
                interceptor.preSend(message, channel);

        StompHeaderAccessor resultAccessor =
                StompHeaderAccessor.getAccessor(
                        result,
                        StompHeaderAccessor.class
                );

        assertNotNull(resultAccessor);
        assertNotNull(resultAccessor.getUser());
        assertEquals("alice", resultAccessor.getUser().getName());
    }

    @Test
    void connectRejectsMissingAuthorization() {
        WebSocketAuthInterceptor interceptor =
                new WebSocketAuthInterceptor(
                        jwtService,
                        projectMemberService
                );

        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.CONNECT);

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(jwtService);
    }

    @Test
    void projectSubscriptionRequiresReadAccess() {
        when(jwtService.extractUsername("token"))
                .thenReturn("alice");

        WebSocketAuthInterceptor interceptor =
                new WebSocketAuthInterceptor(
                        jwtService,
                        projectMemberService
                );

        StompHeaderAccessor connect =
                StompHeaderAccessor.create(StompCommand.CONNECT);
        connect.setLeaveMutable(true);
        connect.setNativeHeader(
                "Authorization",
                "Bearer token"
        );

        Message<byte[]> connectMessage =
                MessageBuilder.createMessage(
                        new byte[0],
                        connect.getMessageHeaders()
                );

        Message<?> authenticated =
                interceptor.preSend(
                        connectMessage,
                        channel
                );

        StompHeaderAccessor authenticatedAccessor =
                StompHeaderAccessor.getAccessor(
                        authenticated,
                        StompHeaderAccessor.class
                );

        StompHeaderAccessor subscribe =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setLeaveMutable(true);
        subscribe.setUser(authenticatedAccessor.getUser());
        subscribe.setDestination(
                "/topic/project/42/code"
        );

        Message<byte[]> subscribeMessage =
                MessageBuilder.createMessage(
                        new byte[0],
                        subscribe.getMessageHeaders()
                );

        interceptor.preSend(subscribeMessage, channel);

        verify(projectMemberService)
                .requireReadAccess(42L, "alice");
    }

    @Test
    void projectSubscriptionRejectsUnknownDestination() {
        WebSocketAuthInterceptor interceptor =
                new WebSocketAuthInterceptor(
                        jwtService,
                        projectMemberService
                );

        var user =
                new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(
                                "alice",
                                null,
                                java.util.List.of()
                        );

        StompHeaderAccessor subscribe =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setUser(user);
        subscribe.setDestination("/topic/admin/secrets");

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        subscribe.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, channel)
        );

        verifyNoInteractions(projectMemberService);
    }


    @Test
    void userSubscriptionCannotTargetAnotherUser() {
        WebSocketAuthInterceptor interceptor =
                new WebSocketAuthInterceptor(
                        jwtService,
                        projectMemberService
                );

        var user =
                new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(
                                "alice",
                                null,
                                java.util.List.of()
                        );

        StompHeaderAccessor subscribe =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setUser(user);
        subscribe.setDestination("/user/bob/queue/notifications");

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        subscribe.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(message, channel)
        );
    }

    @Test
    void sendAllowsOnlyKnownApplicationDestinations() {
        WebSocketAuthInterceptor interceptor =
                new WebSocketAuthInterceptor(
                        jwtService,
                        projectMemberService
                );

        var user =
                new org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken(
                                "alice",
                                null,
                                java.util.List.of()
                        );

        StompHeaderAccessor send =
                StompHeaderAccessor.create(StompCommand.SEND);
        send.setLeaveMutable(true);
        send.setUser(user);
        send.setDestination("/app/chat");

        Message<byte[]> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        send.getMessageHeaders()
                );

        assertDoesNotThrow(
                () -> interceptor.preSend(message, channel)
        );

        send.setDestination("/app/admin");
        Message<byte[]> invalidMessage =
                MessageBuilder.createMessage(
                        new byte[0],
                        send.getMessageHeaders()
                );

        assertThrows(
                MessagingException.class,
                () -> interceptor.preSend(invalidMessage, channel)
        );
    }
}
