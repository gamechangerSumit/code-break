package com.codebreak.backend.history;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceVersionService {
    private final WorkspaceVersionRepository repository;
    private final WorkspaceService workspaceService;
    private final ProjectMemberService memberService;

    @Transactional(readOnly = true)
    public List<VersionResponse> list(Long projectId, String path, String username) {
        memberService.requireReadAccess(projectId, username);
        String cleanPath = normalize(path);
        return repository.findTop100ByProjectIdAndFilePathOrderByCreatedAtDesc(projectId, cleanPath)
                .stream().map(VersionResponse::from).toList();
    }

    @Transactional
    public VersionResponse snapshot(Long projectId, String path, String username, String message) {
        memberService.requireWriteAccess(projectId, username);
        String cleanPath = normalize(path);
        String content = workspaceService.getFileContent(projectId, cleanPath, username);
        long next = repository.countByProjectIdAndFilePath(projectId, cleanPath) + 1;
        WorkspaceVersion saved = repository.save(WorkspaceVersion.builder()
                .projectId(projectId).filePath(cleanPath).versionNumber(next)
                .content(content).createdBy(username)
                .message(message == null || message.isBlank() ? "Manual snapshot" : message.trim())
                .build());
        return VersionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public VersionContent get(Long projectId, Long versionId, String username) {
        memberService.requireReadAccess(projectId, username);
        WorkspaceVersion version = repository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
        return new VersionContent(VersionResponse.from(version), version.getContent());
    }

    @Transactional
    public VersionResponse restore(Long projectId, Long versionId, String username) {
        memberService.requireWriteAccess(projectId, username);
        WorkspaceVersion version = repository.findByIdAndProjectId(versionId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found"));
        workspaceService.saveFile(projectId, version.getFilePath(), version.getContent(), username);
        long next = repository.countByProjectIdAndFilePath(projectId, version.getFilePath()) + 1;
        WorkspaceVersion restored = repository.save(WorkspaceVersion.builder()
                .projectId(projectId).filePath(version.getFilePath()).versionNumber(next)
                .content(version.getContent()).createdBy(username)
                .message("Restored from version " + version.getVersionNumber()).build());
        return VersionResponse.from(restored);
    }

    private String normalize(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") ||
                path.contains("\\") || path.contains("../") || path.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid file path");
        }
        return path.trim();
    }

    public record VersionResponse(Long id, String filePath, Long versionNumber,
                                  String createdBy, String message, java.time.LocalDateTime createdAt) {
        static VersionResponse from(WorkspaceVersion version) {
            return new VersionResponse(version.getId(), version.getFilePath(), version.getVersionNumber(),
                    version.getCreatedBy(), version.getMessage(), version.getCreatedAt());
        }
    }
    public record VersionContent(VersionResponse version, String content) {}
}
