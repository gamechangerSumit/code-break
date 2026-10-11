package com.codebreak.backend.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import static org.mockito.Mockito.*;

class WebSocketConfigTest {

    @Test
    void configuresBoundedWebSocketTransportLimits() {
        WebSocketConfig config = new WebSocketConfig(
                mock(WebSocketAuthInterceptor.class),
                "http://localhost:5173"
        );
        WebSocketTransportRegistration registration =
                mock(WebSocketTransportRegistration.class);

        when(registration.setMessageSizeLimit(64 * 1024))
                .thenReturn(registration);
        when(registration.setSendBufferSizeLimit(512 * 1024))
                .thenReturn(registration);
        when(registration.setSendTimeLimit(15_000))
                .thenReturn(registration);

        config.configureWebSocketTransport(registration);

        verify(registration).setMessageSizeLimit(64 * 1024);
        verify(registration).setSendBufferSizeLimit(512 * 1024);
        verify(registration).setSendTimeLimit(15_000);
    }
}
