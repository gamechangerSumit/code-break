package com.codebreak.backend.websocket;

import org.springframework.context.annotation.Configuration;

import org.springframework.messaging.simp.config.MessageBrokerRegistry;

import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import org.springframework.messaging.simp.config.ChannelRegistration;


@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {


    private final WebSocketAuthInterceptor
            webSocketAuthInterceptor;


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
                        "*"
                );

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