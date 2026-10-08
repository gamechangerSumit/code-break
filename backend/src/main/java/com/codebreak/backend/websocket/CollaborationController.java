package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.workspace.RedisWorkspaceService;
import com.codebreak.backend.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
public class CollaborationController {

    private final SimpMessagingTemplate messagingTemplate;

    private final ProjectMemberService projectMemberService;

    private final RedisWorkspaceService redisWorkspaceService;

    private final WorkspaceService workspaceService;


    /**
     * Per-project/per-file JVM locks.
     *
     * This prevents two websocket messages for the same file
     * from reading the same old content and overwriting each other.
     *
     * NOTE:
     * This is safe for a single backend instance.
     * For multiple backend instances a distributed Redis lock
     * or CRDT/OT layer should be introduced later.
     */
    private final Map<String, Object> fileLocks =
            new ConcurrentHashMap<>();


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


        if (filePath.isBlank()) {
            return;
        }


        // =================================================
        // SERVER-SIDE WRITE PERMISSION
        // =================================================

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        String lockKey =
                projectId +
                        ":" +
                        filePath;


        Object lock =
                fileLocks.computeIfAbsent(
                        lockKey,
                        ignored -> new Object()
                );


        synchronized (lock) {

            // =============================================
            // LOAD AUTHORITATIVE CURRENT CONTENT
            //
            // Redis dirty state is checked first by
            // WorkspaceService.
            //
            // MinIO is used when there is no dirty state.
            // =============================================

            String currentContent =
                    workspaceService.getFileContent(
                            projectId,
                            filePath,
                            username
                    );


            if (currentContent == null) {
                currentContent = "";
            }


            // =============================================
            // APPLY INCREMENTAL CHANGES
            // =============================================

            String mergedContent =
                    currentContent;


            List<CollaborationMessage.CodeChange> changes =
                    message.getChanges();


            if (
                    changes != null &&
                            !changes.isEmpty()
            ) {

                mergedContent =
                        applyChanges(
                                currentContent,
                                changes
                        );

            } else if (
                    message.getContent() != null
            ) {

                /*
                 * Backward compatibility with the old client.
                 *
                 * Old frontend sends complete content.
                 * We accept it temporarily.
                 *
                 * New frontend should always send `changes`.
                 */
                mergedContent =
                        message.getContent();
            }


            // =============================================
            // SAVE TEMPORARY AUTHORITATIVE STATE
            // =============================================

            redisWorkspaceService.saveDirtyContent(
                    projectId,
                    filePath,
                    mergedContent
            );

            redisWorkspaceService.markActive(
                    projectId
            );


            // =============================================
            // BROADCAST
            // =============================================

            CollaborationMessage outgoing =
                    new CollaborationMessage();

            outgoing.setType(
                    "CODE_CHANGE"
            );

            outgoing.setProjectId(
                    projectId
            );

            outgoing.setFilePath(
                    filePath
            );

            outgoing.setUsername(
                    username
            );

            /*
             * Keep complete content temporarily so the current
             * frontend can consume the message safely.
             *
             * Once App.tsx is fully migrated to delta application,
             * this can be removed to reduce network traffic.
             */
            outgoing.setContent(
                    mergedContent
            );

            outgoing.setChanges(
                    changes
            );


            messagingTemplate.convertAndSend(
                    "/topic/project/" +
                            projectId +
                            "/code",
                    outgoing
            );
        }
    }


    // =====================================================
    // APPLY MONACO CHANGES
    // =====================================================

    private String applyChanges(
            String currentContent,
            List<CollaborationMessage.CodeChange> incomingChanges
    ) {

        if (incomingChanges == null ||
                incomingChanges.isEmpty()) {

            return currentContent;
        }


        String result =
                currentContent;


        /*
         * Monaco offsets are based on the document state at the
         * time the change event was generated.
         *
         * Applying changes from right to left prevents earlier
         * replacements from shifting the offsets of later ones.
         */
        List<CollaborationMessage.CodeChange> changes =
                new ArrayList<>(
                        incomingChanges
                );


        changes.sort(
                Comparator.comparing(
                        CollaborationMessage.CodeChange::getRangeOffset,
                        Comparator.nullsLast(
                                Comparator.reverseOrder()
                        )
                )
        );


        for (
                CollaborationMessage.CodeChange change
                : changes
        ) {

            if (change == null) {
                continue;
            }


            Integer offsetValue =
                    change.getRangeOffset();

            Integer lengthValue =
                    change.getRangeLength();


            int offset =
                    offsetValue == null
                            ? 0
                            : offsetValue;


            int length =
                    lengthValue == null
                            ? 0
                            : lengthValue;


            String text =
                    change.getText() == null
                            ? ""
                            : change.getText();


            if (offset < 0) {
                throw new IllegalArgumentException(
                        "Invalid edit offset"
                );
            }


            if (length < 0) {
                throw new IllegalArgumentException(
                        "Invalid edit length"
                );
            }


            if (
                    offset >
                            result.length()
            ) {

                throw new IllegalArgumentException(
                        "Edit offset is outside document"
                );
            }


            long endLong =
                    (long) offset +
                            length;


            if (
                    endLong >
                            result.length()
            ) {

                throw new IllegalArgumentException(
                        "Edit range is outside document"
                );
            }


            int end =
                    (int) endLong;


            result =
                    result.substring(
                            0,
                            offset
                    ) +
                            text +
                            result.substring(
                                    end
                            );
        }


        return result;
    }


    // =====================================================
    // WORKSPACE STRUCTURE EVENTS
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


        // =================================================
        // RENAME
        //
        // Current WorkspaceService does not expose a rename
        // operation, so do not fake persistence here.
        // The local client may still handle its own rename.
        // =================================================

        if (
                "WORKSPACE_RENAME".equals(type)
        ) {

            messagingTemplate.convertAndSend(
                    "/topic/project/" +
                            projectId +
                            "/workspace",
                    message
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


        projectMemberService.requireReadAccess(
                projectId,
                username
        );


        WorkspaceService.WorkspaceSnapshotResponse snapshot =
                workspaceService.getWorkspace(
                        projectId,
                        username
                );


        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/workspace",
                snapshot
        );


        redisWorkspaceService.markActive(
                projectId
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

        workspaceService.importWorkspace(
                projectId,

                message.getFiles() == null
                        ? List.of()
                        : message.getFiles()
                          .stream()
                          .map(
                                  file ->
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