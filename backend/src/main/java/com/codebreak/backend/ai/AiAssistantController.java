package com.codebreak.backend.ai;

import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAssistantController {
    private final ProjectMemberService members;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.ai.api-key:}")
    private String apiKey;
    @Value("${app.ai.base-url:https://api.openai.com}")
    private String baseUrl;
    @Value("${app.ai.model:gpt-4.1-mini}")
    private String model;

    @PostMapping("/projects/{projectId}/assist")
    public AssistantResponse assist(@PathVariable Long projectId,
                                    @RequestBody AssistantRequest request,
                                    Authentication authentication) {
        members.requireReadAccess(projectId, authentication.getName());
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI assistant is not configured. Set AI_API_KEY on the server.");
        }
        if (request == null || request.prompt() == null || request.prompt().isBlank() ||
                request.prompt().length() > 12000 ||
                (request.code() != null && request.code().length() > 30000)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt or code is empty or too large");
        }

        String userContent = "Task:\n" + request.prompt().trim() +
                "\n\nFile: " + safe(request.filePath()) +
                "\n\nCode:\n" + safe(request.code());
        Map<?, ?> response;
        try {
            response = restClientBuilder.clone().baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .build()
                    .post().uri("/v1/chat/completions")
                    .body(Map.of(
                            "model", model,
                            "temperature", 0.2,
                            "messages", List.of(
                                    Map.of("role", "system", "content",
                                            "You are a careful coding assistant inside Code Break. Explain assumptions, provide safe, focused code suggestions, and never claim to have changed files. Return concise Markdown."),
                                    Map.of("role", "user", "content", userContent)
                            )
                    )).retrieve().body(Map.class);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI provider request failed: " + exception.getMessage());
        }

        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider returned an empty response");
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI provider response was invalid");
        }
        Object answer = message.get("content");
        return new AssistantResponse(answer == null ? "" : String.valueOf(answer), model);
    }

    private String safe(String value) { return value == null ? "" : value; }
    public record AssistantRequest(String prompt, String code, String filePath) {}
    public record AssistantResponse(String answer, String model) {}
}
