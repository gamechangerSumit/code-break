package com.codebreak.backend.websocket;

public record ChatMessage(

        Long projectId,

        String username,

        String content

) {
}