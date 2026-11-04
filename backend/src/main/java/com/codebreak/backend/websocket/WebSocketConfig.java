package com.codebreak.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {


    private final WebSocketAuthInterceptor
            webSocketAuthInterceptor;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;


    public WebSocketConfig(
            WebSocketAuthInterceptor
                    webSocketAuthInterceptor
    ) {

        this.webSocketAuthInterceptor =
                webSocketAuthInterceptor;

    }


    // =========================================
    // MESSAGE BROKER
    // =========================================

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

        // Preserve per-session publication order for sequential editor deltas.
        registry.setPreservePublishOrder(true);

    }


    // =========================================
    // WEBSOCKET ENDPOINT
    // =========================================

    @Override
    public void registerStompEndpoints(

            StompEndpointRegistry registry

    ) {

        registry

                .addEndpoint(
                        "/ws"
                )

                .setAllowedOriginPatterns(
                        Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toArray(String[]::new)
                );

    }


    // =========================================
    // WEBSOCKET RESOURCE LIMITS
    // =========================================

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        // Allow a 2 MiB document plus STOMP/JSON framing overhead.
        registration.setMessageSizeLimit(3 * 1024 * 1024);
        registration.setSendBufferSizeLimit(4 * 1024 * 1024);
        registration.setSendTimeLimit(15_000);
    }


    // =========================================
    // CHANNEL INTERCEPTOR
    // =========================================

    @Override
    public void configureClientInboundChannel(

            ChannelRegistration registration

    ) {

        registration.interceptors(

                webSocketAuthInterceptor

        );

    }
}