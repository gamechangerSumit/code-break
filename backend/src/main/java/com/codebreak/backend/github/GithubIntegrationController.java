package com.codebreak.backend.github;

import com.codebreak.backend.user.User;
import com.codebreak.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.*;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubIntegrationController {
    private final GithubConnectionRepository connections;
    private final UserRepository users;
    private final RestClient.Builder restClientBuilder;
    @Value("${app.github.encryption-key:}") private String encryptionKey;

    @PostMapping("/connect")
    public ConnectionResponse connect(@RequestBody ConnectRequest request, Authentication auth) {
        if (request == null || request.token() == null || request.token().isBlank() || request.token().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid GitHub token is required");
        }
        Map<?, ?> githubUser = githubClient(request.token()).get().uri("/user").retrieve().body(Map.class);
        if (githubUser == null || githubUser.get("login") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub did not return an account");
        }
        User user = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        EncryptedSecret encrypted = encrypt(request.token());
        GithubConnection connection = connections.findByUserId(user.getId()).orElseGet(GithubConnection::new);
        connection.setUser(user);
        connection.setGithubUsername(String.valueOf(githubUser.get("login")));
        connection.setEncryptedToken(encrypted.cipherText());
        connection.setTokenIv(encrypted.iv());
        connections.save(connection);
        return new ConnectionResponse(connection.getGithubUsername(), connection.getConnectedAt());
    }

    @GetMapping("/connection")
    public ConnectionResponse connection(Authentication auth) {
        User user = currentUser(auth);
        GithubConnection connection = connections.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub is not connected"));
        return new ConnectionResponse(connection.getGithubUsername(), connection.getConnectedAt());
    }

    @GetMapping("/repositories")
    public List<RepositoryResponse> repositories(Authentication auth) {
        User user = currentUser(auth);
        GithubConnection connection = connections.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub is not connected"));
        String token = decrypt(connection.getEncryptedToken(), connection.getTokenIv());
        List<?> repos = githubClient(token).get().uri("/user/repos?per_page=100&sort=updated").retrieve().body(List.class);
        if (repos == null) return List.of();
        return repos.stream().filter(Map.class::isInstance).map(Map.class::cast).map(repo ->
                new RepositoryResponse(String.valueOf(repo.get("full_name")),
                        String.valueOf(repo.get("html_url")),
                        Boolean.TRUE.equals(repo.get("private")),
                        repo.get("default_branch") == null ? "main" : String.valueOf(repo.get("default_branch"))))
                .toList();
    }

    @DeleteMapping("/connection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(Authentication auth) {
        connections.deleteByUserId(currentUser(auth).getId());
    }

    private User currentUser(Authentication auth) {
        return users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }
    private RestClient githubClient(String token) {
        return restClientBuilder.clone().baseUrl("https://api.github.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }
    private EncryptedSecret encrypt(String token) {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            if (key.length != 32) throw new IllegalStateException("GITHUB_TOKEN_ENCRYPTION_KEY must be base64-encoded 32 bytes");
            byte[] iv = new byte[12]; new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new EncryptedSecret(Base64.getEncoder().encodeToString(cipher.doFinal(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                    Base64.getEncoder().encodeToString(iv));
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "GitHub token encryption is not configured"); }
    }
    private String decrypt(String cipherText, String ivValue) {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Base64.getDecoder().decode(ivValue)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(cipherText)), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not decrypt stored GitHub token"); }
    }
    public record ConnectRequest(String token) {}
    public record ConnectionResponse(String githubUsername, java.time.LocalDateTime connectedAt) {}
    public record RepositoryResponse(String fullName, String url, boolean isPrivate, String defaultBranch) {}
    private record EncryptedSecret(String cipherText, String iv) {}
}
