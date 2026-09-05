package com.codebreak.backend.workspace;

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
            @RequestBody WorkspaceFileSaveRequest request,
            Authentication authentication
    ) {

        WorkspaceService.WorkspaceFileResponse response =
                workspaceService.saveFile(
                        projectId,
                        request.path(),
                        request.content(),
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
            @RequestBody WorkspaceFolderRequest request,
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


    // =====================================================
    // REQUEST DTOs
    // =====================================================

    public record WorkspaceFileSaveRequest(
            String path,
            String content
    ) {}


    public record WorkspaceFolderRequest(
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