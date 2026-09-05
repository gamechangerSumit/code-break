package com.codebreak.backend.websocket;

import com.codebreak.backend.auth.JwtService;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import org.springframework.messaging.support.ChannelInterceptor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import org.springframework.security.core.authority.AuthorityUtils;

import org.springframework.stereotype.Component;

import java.security.Principal;


@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor
        implements ChannelInterceptor {


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

                return message;

            }


            if (
                    !authorization.startsWith(
                            "Bearer "
                    )
            ) {

                System.err.println(
                        "[WS AUTH] Invalid Authorization format"
                );

                return message;

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

                return message;

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

                    return message;

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

                System.err.println(
                        "[WS AUTH] Invalid JWT: "
                                + exception.getMessage()
                );

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