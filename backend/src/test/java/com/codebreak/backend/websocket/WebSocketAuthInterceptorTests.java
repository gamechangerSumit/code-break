package com.codebreak.backend.websocket;

import com.codebreak.backend.auth.JwtService;
import com.codebreak.backend.project.ProjectMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class WebSocketAuthInterceptorTests {

    private JwtService jwtService;
    private ObjectProvider<ProjectMemberService> memberProvider;
    private ProjectMemberService memberService;
    private WebSocketAuthInterceptor interceptor;
    private MessageChannel channel;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jwtService = mock(JwtService.class);
        memberProvider = mock(ObjectProvider.class);
        memberService = mock(ProjectMemberService.class);
        when(memberProvider.getObject()).thenReturn(memberService);
        interceptor = new WebSocketAuthInterceptor(jwtService, memberProvider);
        channel = mock(MessageChannel.class);
    }

    @Test
    void rejectsConnectWithoutAuthorizationHeader() {
        Message<?> message = stompMessage(StompCommand.CONNECT, null, null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void rejectsNonBearerAuthorizationHeader() {
        Message<?> message = stompMessage(StompCommand.CONNECT, "Basic abc", null);
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
    }

    @Test
    void rejectsOversizedJwtBeforeParsing() {
        Message<?> message = stompMessage(
                StompCommand.CONNECT,
                "Bearer " + "x".repeat(8193),
                null
        );
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
        verifyNoInteractions(jwtService);
    }

    @Test
    void authenticatesConnectAndSetsPrincipal() {
        when(jwtService.extractUsername("valid-token")).thenReturn("alice");
        Message<?> message = stompMessage(StompCommand.CONNECT, "Bearer valid-token", null);

        Message<?> result = interceptor.preSend(message, channel);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);

        assertNotNull(accessor.getUser());
        assertEquals("alice", accessor.getUser().getName());
    }

    @Test
    void rejectsProjectSubscriptionWithoutAuthenticatedPrincipal() {
        Message<?> message = stompMessage(
                StompCommand.SUBSCRIBE,
                null,
                "/topic/project/42/code"
        );
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
        verifyNoInteractions(memberService);
    }

    @Test
    void checksMembershipBeforeAllowingProjectSubscription() {
        Message<?> message = stompMessage(
                StompCommand.SUBSCRIBE,
                null,
                "/topic/project/42/code"
        );
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        accessor.setUser(new UsernamePasswordAuthenticationToken("alice", null));

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        verify(memberService).requireReadAccess(42L, "alice");
    }

    @Test
    void rejectsMalformedProjectSubscription() {
        Message<?> message = stompMessage(
                StompCommand.SUBSCRIBE,
                null,
                "/topic/project/not-a-number/code"
        );
        StompHeaderAccessor accessor = StompHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        accessor.setUser(new UsernamePasswordAuthenticationToken("alice", null));

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel));
        verifyNoInteractions(memberService);
    }

    private Message<byte[]> stompMessage(StompCommand command, String authorization, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
