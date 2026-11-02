package com.codebreak.backend.github;

import com.codebreak.backend.user.User;
import com.codebreak.backend.user.UserRepository;
import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.activity.ProjectActivityService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.codebreak.backend.workspace.WorkspaceService;
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
import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/github")
@RequiredArgsConstructor
public class GithubIntegrationController {
    private final GithubConnectionRepository connections;
    private final UserRepository users;
    private final RestClient.Builder restClientBuilder;
    private final ProjectMemberService members;
    private final WorkspaceService workspaceService;
    private final ProjectActivityService activity;
    private final SimpMessagingTemplate messagingTemplate;
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

    @PostMapping("/projects/{projectId}/import")
    public ImportResponse importRepository(@PathVariable Long projectId,
                                           @RequestBody ImportRequest request,
                                           Authentication auth) {
        members.requireWriteAccess(projectId, auth.getName());
        if (request == null || request.fullName() == null ||
                !request.fullName().matches("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository must be owner/name");
        }
        User user = currentUser(auth);
        GithubConnection connection = connections.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub is not connected"));
        String token = decrypt(connection.getEncryptedToken(), connection.getTokenIv());
        String[] parts = request.fullName().split("/", 2);
        String branch = request.branch() == null || request.branch().isBlank() ? "main" : request.branch().trim();
        RestClient client = githubClient(token);
        Map<?, ?> treeResponse = client.get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1", parts[0], parts[1], branch)
                .retrieve().body(Map.class);
        if (treeResponse == null || Boolean.TRUE.equals(treeResponse.get("truncated")) ||
                !(treeResponse.get("tree") instanceof List<?> tree)) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub repository tree is unavailable or too large");
        }
        List<WorkspaceService.WorkspaceImportFile> files = new ArrayList<>();
        Set<String> folders = new LinkedHashSet<>();
        long totalBytes = 0;
        for (Object item : tree) {
            if (!(item instanceof Map<?, ?> entry) || !"blob".equals(entry.get("type"))) continue;
            String filePath = String.valueOf(entry.get("path"));
            if (!safeRepositoryPath(filePath)) continue;
            Object sizeValue = entry.get("size");
            long size = sizeValue instanceof Number n ? n.longValue() : 0;
            if (size < 0 || size > 512_000 || files.size() >= 200 || totalBytes + size > 5_000_000) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Import is limited to 200 files and 5 MB total");
            }
            String blobUrl = String.valueOf(entry.get("url"));
            URI uri = URI.create(blobUrl);
            if (!"api.github.com".equalsIgnoreCase(uri.getHost())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unexpected GitHub blob URL");
            }
            Map<?, ?> blob = client.get().uri(uri).retrieve().body(Map.class);
            if (blob == null || !"base64".equals(blob.get("encoding")) || blob.get("content") == null) continue;
            byte[] decoded;
            try { decoded = Base64.getMimeDecoder().decode(String.valueOf(blob.get("content"))); }
            catch (IllegalArgumentException exception) { continue; }
            totalBytes += decoded.length;
            if (totalBytes > 5_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Import exceeds 5 MB");
            String content = new String(decoded, StandardCharsets.UTF_8);
            files.add(new WorkspaceService.WorkspaceImportFile(filePath,
                    filePath.substring(filePath.lastIndexOf('/') + 1), content));
            int slash = filePath.lastIndexOf('/');
            while (slash > 0) {
                folders.add(filePath.substring(0, slash));
                slash = filePath.lastIndexOf('/', slash - 1);
            }
        }
        workspaceService.importWorkspace(projectId, files, new ArrayList<>(folders), auth.getName());
        activity.record(projectId, auth.getName(), "GITHUB_IMPORT",
                "Imported " + files.size() + " files from " + request.fullName());
        messagingTemplate.convertAndSend("/topic/project/" + projectId + "/workspace",
                new WorkspaceRefreshSignal("WORKSPACE_REFRESH", projectId, auth.getName()));
        return new ImportResponse(request.fullName(), files.size(), totalBytes);
    }

    @PostMapping("/projects/{projectId}/push")
    public PushResponse pushWorkspace(@PathVariable Long projectId,
                                      @RequestBody PushRequest request,
                                      Authentication auth) {
        members.requireWriteAccess(projectId, auth.getName());
        if (request == null || request.fullName() == null ||
                !request.fullName().matches("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Repository must be owner/name");
        }
        String branchName = request.branch() == null || request.branch().isBlank() ? "main" : request.branch().trim();
        if (!branchName.matches("^[A-Za-z0-9._/-]{1,200}$") ||
                branchName.contains("..") || branchName.startsWith("/") || branchName.endsWith("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Git branch name");
        }
        String commitMessage = request.commitMessage() == null || request.commitMessage().isBlank()
                ? "Update from Code Break" : request.commitMessage().trim();
        if (commitMessage.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commit message must be at most 200 characters");
        }

        GithubConnection connection = connections.findByUserId(currentUser(auth).getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GitHub is not connected"));
        RestClient client = githubClient(decrypt(connection.getEncryptedToken(), connection.getTokenIv()));
        String[] parts = request.fullName().split("/", 2);

        Map<?, ?> ref = client.get().uri("/repos/{owner}/{repo}/git/ref/heads/{branch}",
                parts[0], parts[1], branchName).retrieve().body(Map.class);
        if (ref == null || !(ref.get("object") instanceof Map<?, ?> refObject) || refObject.get("sha") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub branch reference was invalid");
        }
        String parentSha = String.valueOf(refObject.get("sha"));
        Map<?, ?> parentCommit = client.get().uri("/repos/{owner}/{repo}/git/commits/{sha}",
                parts[0], parts[1], parentSha).retrieve().body(Map.class);
        if (parentCommit == null || !(parentCommit.get("tree") instanceof Map<?, ?> parentTree) ||
                parentTree.get("sha") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not read GitHub base tree");
        }

        WorkspaceService.WorkspaceSnapshotResponse snapshot =
                workspaceService.getWorkspace(projectId, auth.getName());
        List<Map<String, Object>> treeItems = new ArrayList<>();
        long totalBytes = 0;
        int pushedFiles = 0;
        for (WorkspaceService.WorkspaceFileResponse file : snapshot.files()) {
            String filePath = file.path();
            if (!safeRepositoryPath(filePath)) continue;
            String content = workspaceService.getFileContent(projectId, filePath, auth.getName());
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            totalBytes += bytes.length;
            if (bytes.length > 512_000 || totalBytes > 5_000_000 || pushedFiles >= 200) {
                throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Push is limited to 200 files and 5 MB total");
            }
            Map<?, ?> blob = client.post().uri("/repos/{owner}/{repo}/git/blobs", parts[0], parts[1])
                    .body(Map.of("content", Base64.getEncoder().encodeToString(bytes), "encoding", "base64"))
                    .retrieve().body(Map.class);
            if (blob == null || blob.get("sha") == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub did not create a file blob");
            }
            treeItems.add(Map.of("path", filePath, "mode", "100644", "type", "blob", "sha", String.valueOf(blob.get("sha"))));
            pushedFiles++;
        }

        Map<?, ?> tree = client.post().uri("/repos/{owner}/{repo}/git/trees", parts[0], parts[1])
                .body(Map.of("base_tree", String.valueOf(parentTree.get("sha")), "tree", treeItems))
                .retrieve().body(Map.class);
        if (tree == null || tree.get("sha") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub did not create a commit tree");
        }
        Map<?, ?> commit = client.post().uri("/repos/{owner}/{repo}/git/commits", parts[0], parts[1])
                .body(Map.of("message", commitMessage, "tree", String.valueOf(tree.get("sha")),
                        "parents", List.of(parentSha)))
                .retrieve().body(Map.class);
        if (commit == null || commit.get("sha") == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub did not create a commit");
        }
        String commitSha = String.valueOf(commit.get("sha"));
        client.patch().uri("/repos/{owner}/{repo}/git/refs/heads/{branch}",
                        parts[0], parts[1], branchName)
                .body(Map.of("sha", commitSha, "force", false))
                .retrieve().toBodilessEntity();

        activity.record(projectId, auth.getName(), "GITHUB_PUSH",
                "Pushed " + pushedFiles + " files to " + request.fullName() + " (" + branchName + ")");
        return new PushResponse(request.fullName(), branchName, commitSha, pushedFiles, totalBytes);
    }

    public record PushRequest(String fullName, String branch, String commitMessage) {}
    public record PushResponse(String repository, String branch, String commitSha, int pushedFiles, long bytes) {}

    private boolean safeRepositoryPath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") ||
                path.contains("..") || path.length() > 1024) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return !lower.startsWith(".git/") && !lower.matches(".*(^|/)\\.env[^/]*.*") &&
                !lower.endsWith(".pem") && !lower.endsWith(".key") && !lower.endsWith(".p12") &&
                !lower.contains("/node_modules/") &&
                !lower.startsWith("node_modules/") && !lower.startsWith("target/") &&
                !lower.startsWith("dist/") && !lower.startsWith(".env");
    }

    public record WorkspaceRefreshSignal(String type, Long projectId, String username) {}
    public record ImportRequest(String fullName, String branch) {}
    public record ImportResponse(String repository, int importedFiles, long bytes) {}

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
