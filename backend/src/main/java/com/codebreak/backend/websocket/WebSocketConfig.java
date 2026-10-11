package com.codebreak.backend.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            WebSocketAuthInterceptor webSocketAuthInterceptor,
            @Value("${app.websocket.allowed-origins}") String configuredOrigins
    ) {
        this.webSocketAuthInterceptor = webSocketAuthInterceptor;
        this.allowedOrigins = Arrays.stream(
                        configuredOrigins.split(",")
                )
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.enableSimpleBroker(
                "/topic",
                "/queue"
        );

        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins);
    }

    @Override
    public void configureWebSocketTransport(
            WebSocketTransportRegistration registration
    ) {
        // Keep the frame bound above the 20 MiB legacy workspace-content cap, with JSON overhead.
        registration.setMessageSizeLimit(21 * 1024 * 1024);
        registration.setSendBufferSizeLimit(512 * 1024);
        registration.setSendTimeLimit(15_000);
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                webSocketAuthInterceptor
        );
    }
}
