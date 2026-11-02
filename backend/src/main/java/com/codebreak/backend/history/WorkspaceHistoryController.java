package com.codebreak.backend.history;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/projects/{projectId}/history")
@RequiredArgsConstructor
public class WorkspaceHistoryController {
    private static final int MAX_SNAPSHOT_CHARS = 1_000_000;

    private final WorkspaceSnapshotRepository snapshots;
    private final ProjectMemberService members;
    private final WorkspaceService workspace;

    @GetMapping
    @Transactional(readOnly = true)
    public List<SnapshotSummary> list(
            @PathVariable Long projectId,
            @RequestParam String path,
            @RequestAttribute(name = "username", required = false) String ignored,
            org.springframework.security.core.Authentication authentication
    ) {
        String username = requireUsername(authentication);
        members.requireReadAccess(projectId, username);
        String normalized = normalizePath(path);
        return snapshots.findTop100ByProjectIdAndFilePathOrderByCreatedAtDesc(projectId, normalized)
                .stream().map(SnapshotSummary::from).toList();
    }

    @PostMapping
    @Transactional
    public SnapshotSummary create(
            @PathVariable Long projectId,
            @RequestParam String path,
            @RequestBody(required = false) CreateSnapshotRequest request,
            org.springframework.security.core.Authentication authentication
    ) {
        String username = requireUsername(authentication);
        members.requireWriteAccess(projectId, username);
        String normalized = normalizePath(path);
        String content = workspace.getFileContent(projectId, normalized, username);
        if (content == null) content = "";
        if (content.length() > MAX_SNAPSHOT_CHARS) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Snapshot exceeds 1 MB");
        }
        WorkspaceSnapshot saved = snapshots.save(WorkspaceSnapshot.builder()
                .projectId(projectId)
                .filePath(normalized)
                .content(content)
                .createdBy(username)
                .message(request == null ? null : request.message())
                .build());
        return SnapshotSummary.from(saved);
    }

    @GetMapping("/{snapshotId}")
    @Transactional(readOnly = true)
    public Map<String, Object> get(
            @PathVariable Long projectId,
            @PathVariable Long snapshotId,
            org.springframework.security.core.Authentication authentication
    ) {
        String username = requireUsername(authentication);
        members.requireReadAccess(projectId, username);
        WorkspaceSnapshot snapshot = findSnapshot(projectId, snapshotId);
        return Map.of(
                "id", snapshot.getId(),
                "path", snapshot.getFilePath(),
                "content", snapshot.getContent(),
                "createdBy", snapshot.getCreatedBy(),
                "message", snapshot.getMessage() == null ? "" : snapshot.getMessage(),
                "createdAt", snapshot.getCreatedAt()
        );
    }

    @PostMapping("/{snapshotId}/restore")
    @Transactional
    public Map<String, Object> restore(
            @PathVariable Long projectId,
            @PathVariable Long snapshotId,
            org.springframework.security.core.Authentication authentication
    ) {
        String username = requireUsername(authentication);
        members.requireWriteAccess(projectId, username);
        WorkspaceSnapshot snapshot = findSnapshot(projectId, snapshotId);
        workspace.saveFile(projectId, snapshot.getFilePath(), snapshot.getContent(), username);
        WorkspaceSnapshot restorePoint = snapshots.save(WorkspaceSnapshot.builder()
                .projectId(projectId)
                .filePath(snapshot.getFilePath())
                .content(snapshot.getContent())
                .createdBy(username)
                .message("Restored snapshot #" + snapshotId)
                .build());
        return Map.of("restored", true, "snapshot", SnapshotSummary.from(restorePoint));
    }

    private WorkspaceSnapshot findSnapshot(Long projectId, Long snapshotId) {
        return snapshots.findByIdAndProjectId(snapshotId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found"));
    }

    private String requireUsername(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authentication.getName();
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File path is required");
        }
        String normalized = path.replace('\\', '/').replaceAll("^/+|/+$", "");
        if (normalized.isBlank() || normalized.contains("../") || normalized.equals("..") || normalized.contains("/..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }
        return normalized;
    }
}
