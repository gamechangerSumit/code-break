package com.codebreak.backend.websocket;

import com.codebreak.backend.auth.JwtService;
import com.codebreak.backend.project.ProjectMemberService;

import org.springframework.beans.factory.ObjectProvider;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.authority.AuthorityUtils;

import org.springframework.stereotype.Component;

import java.security.Principal;


@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor
        implements ChannelInterceptor {


    private final JwtService jwtService;

    private final ObjectProvider<ProjectMemberService> projectMemberService;


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


        if (
                accessor == null
        ) {

            return message;

        }


        // =========================================
        // CONNECT
        // =========================================

        if (
                StompCommand.CONNECT.equals(
                        accessor.getCommand()
                )
        ) {


            String authorization =
                    accessor.getFirstNativeHeader(
                            "Authorization"
                    );


            System.out.println(
                    "[WS AUTH] CONNECT received"
            );


            if (
                    authorization == null ||
                            authorization.isBlank()
            ) {

                System.err.println(
                        "[WS AUTH] Authorization header missing"
                );

                throw new AccessDeniedException("WebSocket authentication is required");

            }


            if (
                    !authorization.startsWith(
                            "Bearer "
                    )
            ) {

                System.err.println(
                        "[WS AUTH] Invalid Authorization format"
                );

                throw new AccessDeniedException("Invalid WebSocket authorization format");

            }


            String token =
                    authorization.substring(
                            7
                    ).trim();


            if (
                    token.isBlank()
            ) {

                System.err.println(
                        "[WS AUTH] Empty JWT"
                );

                throw new AccessDeniedException("WebSocket token is empty");

            }


            try {


                String username =
                        jwtService.extractUsername(
                                token
                        );


                if (
                        username == null ||
                                username.isBlank()
                ) {

                    System.err.println(
                            "[WS AUTH] JWT username missing"
                    );

                    throw new AccessDeniedException("JWT username is missing");

                }


                UsernamePasswordAuthenticationToken authentication =

                        new UsernamePasswordAuthenticationToken(

                                username,

                                null,

                                AuthorityUtils.NO_AUTHORITIES

                        );


                accessor.setUser(
                        authentication
                );


                System.out.println(
                        "[WS AUTH] Authenticated user: "
                                + username
                );


            } catch (
                    Exception exception
            ) {

                throw new AccessDeniedException("Invalid WebSocket token", exception);

            }

        }


        // =========================================
        // PROJECT TOPIC SUBSCRIPTIONS
        // A connected user must be a member before receiving
        // any private project collaboration or chat events.
        // =========================================

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            Principal user = accessor.getUser();
            String destination = accessor.getDestination();

            if (user == null) {
                throw new AccessDeniedException("WebSocket authentication is required");
            }

            if (destination != null && destination.startsWith("/topic/project/")) {
                String[] parts = destination.split("/");
                if (parts.length < 4) {
                    throw new AccessDeniedException("Invalid project subscription");
                }

                try {
                    Long projectId = Long.parseLong(parts[3]);
                    projectMemberService.getObject().requireReadAccess(projectId, user.getName());
                } catch (NumberFormatException exception) {
                    throw new AccessDeniedException("Invalid project subscription", exception);
                }
            }
        }


        // =========================================
        // EVERY MESSAGE
        // =========================================

        Principal principal =
                accessor.getUser();


        if (
                principal != null
        ) {

            System.out.println(
                    "[WS AUTH] User="
                            + principal.getName()
                            + " Command="
                            + accessor.getCommand()
                            + " Destination="
                            + accessor.getDestination()
            );

        }


        return message;

    }

}