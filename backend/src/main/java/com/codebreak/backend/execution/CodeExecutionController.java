package com.codebreak.backend.execution;

import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/execute")
@RequiredArgsConstructor
public class CodeExecutionController {
    private final ProjectMemberService members;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.execution.base-url:}")
    private String runnerUrl;
    @Value("${app.execution.api-key:}")
    private String runnerApiKey;

    private static final Map<String, Integer> LANGUAGE_IDS = Map.of(
            "python", 71,
            "java", 62,
            "cpp", 54,
            "javascript", 63
    );

    @PostMapping
    public ExecutionResponse execute(@PathVariable Long projectId,
                                     @RequestBody ExecutionRequest request,
                                     Authentication authentication) {
        members.requireWriteAccess(projectId, authentication.getName());
        if (runnerUrl == null || runnerUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Code execution is not configured. Set CODE_RUNNER_URL to a private Judge0-compatible service.");
        }
        if (request == null || request.sourceCode() == null || request.sourceCode().isBlank()
                || request.sourceCode().length() > 50000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source code is empty or exceeds 50 KB");
        }
        String language = request.language() == null ? "" : request.language().toLowerCase().trim();
        Integer languageId = LANGUAGE_IDS.get(language);
        if (languageId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported languages: Python, Java, C++, JavaScript");
        }

        try {
            RestClient.Builder builder = restClientBuilder.clone().baseUrl(runnerUrl);
            if (runnerApiKey != null && !runnerApiKey.isBlank()) {
                builder.defaultHeader("X-Auth-Token", runnerApiKey);
            }
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("language_id", languageId);
            payload.put("source_code", request.sourceCode());
            payload.put("stdin", request.stdin() == null ? "" : request.stdin());
            payload.put("cpu_time_limit", 2.0);
            payload.put("cpu_extra_time", 0.5);
            payload.put("wall_time_limit", 5.0);
            payload.put("memory_limit", 131072);
            payload.put("stack_limit", 65536);
            payload.put("max_processes_and_or_threads", 30);
            payload.put("enable_per_process_and_thread_time_limit", true);
            payload.put("enable_per_process_and_thread_memory_limit", true);
            Map<?, ?> result = builder.build().post()
                    .uri("/submissions?base64_encoded=false&wait=true")
                    .body(payload).retrieve().body(Map.class);
            if (result == null) throw new IllegalStateException("Empty runner response");
            return new ExecutionResponse(
                    text(result.get("stdout")), text(result.get("stderr")),
                    text(result.get("compile_output")), text(result.get("message")),
                    result.get("status") instanceof Map<?, ?> status ? text(status.get("description")) : "Unknown",
                    result.get("time"), result.get("memory"), result.get("exit_code")
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Code runner request failed: " + exception.getMessage());
        }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    public record ExecutionRequest(String language, String sourceCode, String stdin) {}
    public record ExecutionResponse(String stdout, String stderr, String compileOutput,
                                    String message, String status, Object time, Object memory,
                                    Object exitCode) {}
}
