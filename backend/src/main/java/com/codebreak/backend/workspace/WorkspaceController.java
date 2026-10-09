package com.codebreak.backend.workspace;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private static final int MAX_IMPORT_FILES = 500;
    private static final int MAX_IMPORT_CONTENT_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMPORT_TOTAL_CONTENT_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILE_CONTENT_BYTES = 5 * 1024 * 1024;

    private final WorkspaceService workspaceService;

    private final SimpMessagingTemplate messagingTemplate;


    // =====================================================
    // GET WORKSPACE METADATA
    // =====================================================

    @GetMapping
    public ResponseEntity<WorkspaceService.WorkspaceSnapshotResponse>
    getWorkspace(
            @PathVariable Long projectId,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                workspaceService.getWorkspace(
                        projectId,
                        authentication.getName()
                )
        );
    }


    // =====================================================
    // GET SINGLE FILE CONTENT
    // =====================================================

    @GetMapping("/file")
    public ResponseEntity<String> getFileContent(
            @PathVariable Long projectId,
            @RequestParam String path,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                workspaceService.getFileContent(
                        projectId,
                        path,
                        authentication.getName()
                )
        );
    }


    // =====================================================
    // SAVE SINGLE FILE
    // =====================================================

    @PutMapping("/file")
    public ResponseEntity<WorkspaceService.WorkspaceFileResponse>
    saveFile(
            @PathVariable Long projectId,
            @Valid @RequestBody WorkspaceFileSaveRequest request,
            Authentication authentication
    ) {

        String content = request.content() == null ? "" : request.content();
        if (content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_FILE_CONTENT_BYTES) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Workspace file content is too large"
            );
        }

        WorkspaceService.WorkspaceFileResponse response =
                workspaceService.saveFile(
                        projectId,
                        request.path(),
                        content,
                        authentication.getName()
                );

        return ResponseEntity.ok(response);
    }


    // =====================================================
    // DELETE FILE
    // =====================================================

    @DeleteMapping("/file")
    public ResponseEntity<Void> deleteFile(
            @PathVariable Long projectId,
            @RequestParam String path,
            Authentication authentication
    ) {

        workspaceService.deleteFile(
                projectId,
                path,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }


    // =====================================================
    // CREATE FOLDER
    // =====================================================

    @PostMapping("/folder")
    public ResponseEntity<WorkspaceService.WorkspaceFolderResponse>
    createFolder(
            @PathVariable Long projectId,
            @Valid @RequestBody WorkspaceFolderRequest request,
            Authentication authentication
    ) {

        WorkspaceService.WorkspaceFolderResponse response =
                workspaceService.createFolder(
                        projectId,
                        request.path(),
                        authentication.getName()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    // =====================================================
    // DELETE FOLDER
    // =====================================================

    @DeleteMapping("/folder")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable Long projectId,
            @RequestParam String path,
            Authentication authentication
    ) {

        workspaceService.deleteFolder(
                projectId,
                path,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }


    // =====================================================
    // COMPLETE WORKSPACE IMPORT
    //
    // Owner uses this when opening a local folder.
    //
    // Actual file contents:
    //     REST -> Spring Boot -> MinIO
    //
    // Metadata:
    //     PostgreSQL
    //
    // Redis:
    //     active/dirty lightweight state
    //
    // STOMP:
    //     only sends WORKSPACE_REFRESH event
    // =====================================================

    @PostMapping("/import")
    public ResponseEntity<WorkspaceService.WorkspaceSnapshotResponse>
    importWorkspace(
            @PathVariable Long projectId,
            @RequestBody WorkspaceImportRequest request,
            Authentication authentication
    ) {

        validateImportRequest(request);

        WorkspaceService.WorkspaceSnapshotResponse response =
                workspaceService.importWorkspace(
                        projectId,
                        request == null ||
                                request.files() == null
                                ? List.of()
                                : request.files(),

                        request == null ||
                                request.folders() == null
                                ? List.of()
                                : request.folders(),

                        authentication.getName()
                );


        // =================================================
        // NOTIFY CONNECTED COLLABORATORS
        //
        // IMPORTANT:
        // No file contents are sent here.
        //
        // Collaborators receive only a lightweight event
        // telling them to request the latest metadata.
        // =================================================

        WorkspaceRefreshMessage refreshMessage =
                new WorkspaceRefreshMessage(
                        "WORKSPACE_REFRESH",
                        projectId,
                        authentication.getName()
                );


        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/workspace",
                refreshMessage
        );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }


    private void validateImportRequest(WorkspaceImportRequest request) {
        if (request == null) {
            return;
        }

        if (request.files() != null && request.files().size() > MAX_IMPORT_FILES) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Workspace import contains too many files"
            );
        }

        if (request.folders() != null && request.folders().size() > MAX_IMPORT_FILES) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Workspace import contains too many folders"
            );
        }

        if (request.folders() != null) {
            for (String folder : request.folders()) {
                if (folder != null && folder.length() > 1000) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Workspace folder path is too long"
                    );
                }
            }
        }

        long totalContentBytes = 0;
        if (request.files() != null) {
            for (WorkspaceService.WorkspaceImportFile file : request.files()) {
                if (file == null) {
                    continue;
                }

                if (file.path() != null && file.path().length() > 1000) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Workspace file path is too long"
                    );
                }

                if (file.name() != null && file.name().length() > 255) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Workspace file name is too long"
                    );
                }

                if (file.content() != null) {
                    int contentBytes = file.content().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                    if (contentBytes > MAX_IMPORT_CONTENT_BYTES) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Workspace file content is too large"
                        );
                    }

                    totalContentBytes += contentBytes;
                    if (totalContentBytes > MAX_IMPORT_TOTAL_CONTENT_BYTES) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Workspace import content exceeds the 20 MiB total limit"
                        );
                    }
                }
            }
        }
    }


    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record WorkspaceFileSaveRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 1000)
            String path,

            @jakarta.validation.constraints.Size(max = MAX_FILE_CONTENT_BYTES)
            String content
    ) {}


    public record WorkspaceFolderRequest(
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 1000)
            String path
    ) {}


    public record WorkspaceImportRequest(
            List<WorkspaceService.WorkspaceImportFile> files,
            List<String> folders
    ) {}


    // =====================================================
    // LIGHTWEIGHT REFRESH EVENT
    // =====================================================

    public record WorkspaceRefreshMessage(
            String type,
            Long projectId,
            String username
    ) {}
}