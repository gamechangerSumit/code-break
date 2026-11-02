package com.codebreak.backend.history;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/versions")
@RequiredArgsConstructor
public class WorkspaceVersionController {
    private final WorkspaceVersionService service;

    @GetMapping
    public List<WorkspaceVersionService.VersionResponse> list(
            @PathVariable Long projectId, @RequestParam String path, Authentication auth) {
        return service.list(projectId, path, auth.getName());
    }

    @PostMapping
    public ResponseEntity<WorkspaceVersionService.VersionResponse> snapshot(
            @PathVariable Long projectId, @RequestBody SnapshotRequest request, Authentication auth) {
        return ResponseEntity.ok(service.snapshot(projectId, request.path(), auth.getName(), request.message()));
    }

    @GetMapping("/{versionId}")
    public WorkspaceVersionService.VersionContent get(
            @PathVariable Long projectId, @PathVariable Long versionId, Authentication auth) {
        return service.get(projectId, versionId, auth.getName());
    }

    @PostMapping("/{versionId}/restore")
    public WorkspaceVersionService.VersionResponse restore(
            @PathVariable Long projectId, @PathVariable Long versionId, Authentication auth) {
        return service.restore(projectId, versionId, auth.getName());
    }

    public record SnapshotRequest(String path, String message) {}
}
