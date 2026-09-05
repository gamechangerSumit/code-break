package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.workspace.RedisWorkspaceService;
import com.codebreak.backend.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class CollaborationController {

    private final SimpMessagingTemplate messagingTemplate;

    private final ProjectMemberService projectMemberService;

    private final RedisWorkspaceService redisWorkspaceService;

    private final WorkspaceService workspaceService;


    // =====================================================
    // LIVE CODE EDIT
    // =====================================================

    @MessageMapping("/edit")
    public void edit(
            CollaborationMessage message,
            Principal principal
    ) {

        if (
                message == null ||
                        principal == null ||
                        message.getProjectId() == null ||
                        message.getFilePath() == null ||
                        message.getFilePath().isBlank()
        ) {
            return;
        }


        Long projectId =
                message.getProjectId();

        String username =
                principal.getName();

        String filePath =
                normalizePath(
                        message.getFilePath()
                );


        // =============================================
        // WRITE PERMISSION
        // =============================================

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        // =============================================
        // SAVE LATEST EDIT IN REDIS
        //
        // This is temporary dirty state.
        // Scheduler persists it to MinIO.
        // =============================================

        redisWorkspaceService.saveDirtyContent(
                projectId,
                filePath,
                message.getContent()
        );


        redisWorkspaceService.markActive(
                projectId
        );


        // =============================================
        // CLEAN MESSAGE
        // =============================================

        message.setProjectId(
                projectId
        );

        message.setFilePath(
                filePath
        );

        message.setUsername(
                username
        );


        // =============================================
        // BROADCAST LIVE EDIT
        // =============================================

        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/code",
                message
        );
    }


    // =====================================================
    // WORKSPACE STRUCTURE EVENTS
    //
    // File/folder create/delete only.
    //
    // Complete project import is REST based.
    // =====================================================

    @MessageMapping("/workspace")
    public void workspace(
            CollaborationMessage message,
            Principal principal
    ) {

        if (
                message == null ||
                        principal == null ||
                        message.getProjectId() == null
        ) {
            return;
        }


        Long projectId =
                message.getProjectId();

        String username =
                principal.getName();


        // =============================================
        // WRITE PERMISSION
        // =============================================

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        String type =
                message.getType();


        if (
                type == null ||
                        type.isBlank()
        ) {
            return;
        }


        message.setUsername(
                username
        );


        // =================================================
        // LEGACY COMPLETE SYNC
        //
        // Kept temporarily so older frontend builds
        // don't immediately break.
        //
        // New frontend does NOT use this path.
        // =================================================

        if (
                "WORKSPACE_SYNC".equals(type)
        ) {

            handleLegacyWorkspaceSync(
                    message,
                    projectId,
                    username
            );

            return;
        }


        // =================================================
        // CREATE / UPDATE FILE
        // =================================================

        if (
                "WORKSPACE_FILE".equals(type)
        ) {

            handleWorkspaceFile(
                    message,
                    projectId,
                    username
            );

            return;
        }


        // =================================================
        // CREATE FOLDER
        // =================================================

        if (
                "WORKSPACE_FOLDER".equals(type)
        ) {

            handleWorkspaceFolder(
                    message,
                    projectId,
                    username
            );

            return;
        }


        // =================================================
        // DELETE FILE
        // =================================================

        if (
                "WORKSPACE_DELETE".equals(type)
        ) {

            handleWorkspaceDelete(
                    message,
                    projectId,
                    username
            );

            return;
        }


        // =================================================
        // DELETE FOLDER
        // =================================================

        if (
                "WORKSPACE_FOLDER_DELETE".equals(type)
        ) {

            handleWorkspaceFolderDelete(
                    message,
                    projectId,
                    username
            );

            return;
        }


        System.out.println(
                "[WS WORKSPACE] Unknown type: " +
                        type
        );
    }


    // =====================================================
    // WORKSPACE SNAPSHOT REQUEST
    // =====================================================

    @MessageMapping("/workspace/request")
    public void requestWorkspace(
            WorkspaceRequest request,
            Principal principal
    ) {

        if (
                request == null ||
                        principal == null ||
                        request.projectId() == null
        ) {
            return;
        }


        Long projectId =
                request.projectId();

        String username =
                principal.getName();


        // =============================================
        // READ PERMISSION
        // =============================================

        projectMemberService.requireReadAccess(
                projectId,
                username
        );


        // =============================================
        // METADATA ONLY
        //
        // No file contents are loaded here.
        // =============================================

        WorkspaceService.WorkspaceSnapshotResponse snapshot =
                workspaceService.getWorkspace(
                        projectId,
                        username
                );


        System.out.println(
                "[WS SNAPSHOT] Request received: " +
                        "User=" + username +
                        " Project=" + projectId +
                        " Files=" + snapshot.files().size() +
                        " Folders=" + snapshot.folders().size()
        );


        // =============================================
        // PRIVATE RESPONSE
        // =============================================

        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/workspace",
                snapshot
        );


        redisWorkspaceService.markActive(
                projectId
        );


        System.out.println(
                "[WS SNAPSHOT] Metadata sent to: " +
                        username
        );
    }


    // =====================================================
    // LEGACY COMPLETE SYNC
    // =====================================================

    private void handleLegacyWorkspaceSync(
            CollaborationMessage message,
            Long projectId,
            String username
    ) {

        int fileCount =
                message.getFiles() == null
                        ? 0
                        : message.getFiles().size();

        int folderCount =
                message.getFolders() == null
                        ? 0
                        : message.getFolders().size();


        System.out.println(
                "[WS WORKSPACE] LEGACY SYNC received: " +
                        "Project=" + projectId +
                        " User=" + username +
                        " Files=" + fileCount +
                        " Folders=" + folderCount
        );


        workspaceService.importWorkspace(
                projectId,

                message.getFiles() == null
                        ? List.of()
                        : message.getFiles()
                          .stream()
                          .map(file ->
                               new WorkspaceService.WorkspaceImportFile(
                                       normalizePath(
                                               file.getPath()
                                       ),
                                       extractName(
                                               file.getPath()
                                       ),
                                       file.getContent()
                               )
                          )
                          .toList(),

                message.getFolders() == null
                        ? List.of()
                        : message.getFolders()
                          .stream()
                          .map(
                                  this::normalizePath
                          )
                          .toList(),

                username
        );


        redisWorkspaceService.markActive(
                projectId
        );


        /*
         * Keep this broadcast for old clients.
         *
         * New frontend uses REST import and receives
         * WORKSPACE_REFRESH from WorkspaceController.
         */

        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/workspace",
                message
        );
    }


    // =====================================================
    // WORKSPACE FILE
    // =====================================================

    private void handleWorkspaceFile(
            CollaborationMessage message,
            Long projectId,
            String username
    ) {

        if (
                message.getFilePath() == null ||
                        message.getFilePath().isBlank()
        ) {
            return;
        }


        String path =
                normalizePath(
                        message.getFilePath()
                );


        /*
         * Structural file operation goes directly
         * to persistent storage.
         *
         * This is NOT the same as a keystroke.
         */

        workspaceService.saveFile(
                projectId,
                path,
                message.getContent(),
                username
        );


        message.setFilePath(
                path
        );


        redisWorkspaceService.markActive(
                projectId
        );


        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/workspace",
                message
        );
    }


    // =====================================================
    // WORKSPACE FOLDER
    // =====================================================

    private void handleWorkspaceFolder(
            CollaborationMessage message,
            Long projectId,
            String username
    ) {

        if (
                message.getFilePath() == null ||
                        message.getFilePath().isBlank()
        ) {
            return;
        }


        String path =
                normalizePath(
                        message.getFilePath()
                );


        workspaceService.createFolder(
                projectId,
                path,
                username
        );


        message.setFilePath(
                path
        );


        redisWorkspaceService.markActive(
                projectId
        );


        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/workspace",
                message
        );
    }


    // =====================================================
    // DELETE FILE
    // =====================================================

    private void handleWorkspaceDelete(
            CollaborationMessage message,
            Long projectId,
            String username
    ) {

        if (
                message.getFilePath() == null ||
                        message.getFilePath().isBlank()
        ) {
            return;
        }


        String path =
                normalizePath(
                        message.getFilePath()
                );


        workspaceService.deleteFile(
                projectId,
                path,
                username
        );


        message.setFilePath(
                path
        );


        redisWorkspaceService.markActive(
                projectId
        );


        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/workspace",
                message
        );
    }


    // =====================================================
    // DELETE FOLDER
    // =====================================================

    private void handleWorkspaceFolderDelete(
            CollaborationMessage message,
            Long projectId,
            String username
    ) {

        if (
                message.getFilePath() == null ||
                        message.getFilePath().isBlank()
        ) {
            return;
        }


        String path =
                normalizePath(
                        message.getFilePath()
                );


        workspaceService.deleteFolder(
                projectId,
                path,
                username
        );


        message.setFilePath(
                path
        );


        redisWorkspaceService.markActive(
                projectId
        );


        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/workspace",
                message
        );
    }


    // =====================================================
    // PATH NORMALIZATION
    // =====================================================

    private String normalizePath(
            String path
    ) {

        if (path == null) {
            return "";
        }


        String normalized =
                path
                        .trim()
                        .replace(
                                "\\",
                                "/"
                        );


        while (
                normalized.startsWith("/")
        ) {
            normalized =
                    normalized.substring(1);
        }


        while (
                normalized.endsWith("/")
        ) {
            normalized =
                    normalized.substring(
                            0,
                            normalized.length() - 1
                    );
        }


        if (
                normalized.isBlank() ||
                        normalized.contains("..") ||
                        normalized.contains("//")
        ) {

            throw new IllegalArgumentException(
                    "Invalid workspace path"
            );
        }


        return normalized;
    }


    // =====================================================
    // FILE NAME
    // =====================================================

    private String extractName(
            String path
    ) {

        String normalized =
                normalizePath(
                        path
                );


        int index =
                normalized.lastIndexOf("/");


        if (index < 0) {
            return normalized;
        }


        return normalized.substring(
                index + 1
        );
    }
}