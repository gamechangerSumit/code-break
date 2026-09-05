package com.codebreak.backend.workspace;

import com.codebreak.backend.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.codebreak.backend.project.ProjectMemberService;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceFileMetadataRepository fileMetadataRepository;

    private final WorkspaceFolderMetadataRepository folderMetadataRepository;

    private final MinioWorkspaceService minioWorkspaceService;

    private final RedisWorkspaceService redisWorkspaceService;

    private final ProjectMemberService projectMemberService;


    // =====================================================
    // GET WORKSPACE METADATA
    // =====================================================

    @Transactional(readOnly = true)
    public WorkspaceSnapshotResponse getWorkspace(
            Long projectId,
            String username
    ) {

        requireProjectReadAccess(
                projectId,
                username
        );


        List<WorkspaceFileMetadata> files =
                fileMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        );


        List<WorkspaceFolderMetadata> folders =
                folderMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        );


        List<WorkspaceFileResponse> fileResponses =
                files.stream()
                        .map(
                                file ->
                                        new WorkspaceFileResponse(
                                                file.getPath(),
                                                file.getName(),
                                                file.getSize(),
                                                file.getVersion()
                                        )
                        )
                        .toList();


        List<WorkspaceFolderResponse> folderResponses =
                folders.stream()
                        .map(
                                folder ->
                                        new WorkspaceFolderResponse(
                                                folder.getPath(),
                                                folder.getName(),
                                                folder.getParentPath()
                                        )
                        )
                        .toList();


        return new WorkspaceSnapshotResponse(
                projectId,
                fileResponses,
                folderResponses
        );
    }


    // =====================================================
    // GET FILE CONTENT
    // =====================================================

    public String getFileContent(
            Long projectId,
            String path,
            String username
    ) {

        requireProjectReadAccess(
                projectId,
                username
        );


        String normalizedPath =
                normalizePath(
                        path
                );


        WorkspaceFileMetadata metadata =
                fileMetadataRepository
                        .findByProjectIdAndPath(
                                projectId,
                                normalizedPath
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "File not found"
                                        )
                        );


        /*
         * If an editor has a newer value in Redis,
         * return that value instead of stale MinIO content.
         *
         * This is important during the small window between
         * a live edit and the persistence scheduler.
         */
        String dirtyContent =
                redisWorkspaceService.getDirtyContent(
                        projectId,
                        normalizedPath
                );


        if (dirtyContent != null) {
            return dirtyContent;
        }


        return minioWorkspaceService.readFile(
                projectId,
                metadata.getPath()
        );
    }


    // =====================================================
    // SAVE FILE
    //
    // Used for structural file creation/update.
    //
    // Normal editor keystrokes do NOT call this method.
    // They go through Redis first.
    // =====================================================

    @Transactional
    public WorkspaceFileResponse saveFile(
            Long projectId,
            String path,
            String content,
            String username
    ) {

        requireProjectWriteAccess(
                projectId,
                username
        );


        String normalizedPath =
                normalizePath(
                        path
                );


        if (normalizedPath.isBlank()) {
            throw badRequest(
                    "File path is required"
            );
        }


        String cleanContent =
                content == null
                        ? ""
                        : content;


        Project project =
                getProject(
                        projectId
                );


        WorkspaceFileMetadata metadata =
                fileMetadataRepository
                        .findByProjectIdAndPath(
                                projectId,
                                normalizedPath
                        )
                        .orElse(null);


        /*
         * Ensure all parent folders exist.
         */
        ensureParentFolders(
                project,
                normalizedPath
        );


        /*
         * Persist actual content first.
         */
        minioWorkspaceService.saveFile(
                projectId,
                normalizedPath,
                cleanContent
        );


        long size =
                cleanContent.getBytes(
                        StandardCharsets.UTF_8
                ).length;


        if (metadata == null) {

            metadata =
                    WorkspaceFileMetadata.builder()
                            .project(project)
                            .path(normalizedPath)
                            .name(extractName(normalizedPath))
                            .objectKey(
                                    minioWorkspaceService.buildObjectKey(
                                            projectId,
                                            normalizedPath
                                    )
                            )
                            .size(size)
                            .version(1L)
                            .build();

        } else {

            metadata.setName(
                    extractName(
                            normalizedPath
                    )
            );

            metadata.setObjectKey(
                    minioWorkspaceService.buildObjectKey(
                            projectId,
                            normalizedPath
                    )
            );

            metadata.setSize(
                    size
            );

            metadata.setVersion(
                    nextVersion(
                            metadata.getVersion()
                    )
            );
        }


        WorkspaceFileMetadata saved =
                fileMetadataRepository.save(
                        metadata
                );


        /*
         * This structural save is already persistent.
         * Remove any old dirty value for this file.
         */
        redisWorkspaceService.clearDirty(
                projectId,
                normalizedPath
        );


        redisWorkspaceService.markActive(
                projectId
        );


        return new WorkspaceFileResponse(
                saved.getPath(),
                saved.getName(),
                saved.getSize(),
                saved.getVersion()
        );
    }


    // =====================================================
    // DELETE FILE
    // =====================================================

    @Transactional
    public void deleteFile(
            Long projectId,
            String path,
            String username
    ) {

        requireProjectWriteAccess(
                projectId,
                username
        );


        String normalizedPath =
                normalizePath(
                        path
                );


        if (normalizedPath.isBlank()) {
            throw badRequest(
                    "File path is required"
            );
        }


        WorkspaceFileMetadata metadata =
                fileMetadataRepository
                        .findByProjectIdAndPath(
                                projectId,
                                normalizedPath
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "File not found"
                                        )
                        );


        /*
         * Delete persistent object.
         */
        minioWorkspaceService.deleteFile(
                projectId,
                normalizedPath
        );


        /*
         * Delete PostgreSQL metadata.
         */
        fileMetadataRepository.delete(
                metadata
        );


        /*
         * Remove possible temporary editor state.
         */
        redisWorkspaceService.clearDirty(
                projectId,
                normalizedPath
        );


        redisWorkspaceService.markActive(
                projectId
        );
    }


    // =====================================================
    // CREATE FOLDER
    // =====================================================

    @Transactional
    public WorkspaceFolderResponse createFolder(
            Long projectId,
            String path,
            String username
    ) {

        requireProjectWriteAccess(
                projectId,
                username
        );


        String normalizedPath =
                normalizePath(
                        path
                );


        if (normalizedPath.isBlank()) {
            throw badRequest(
                    "Folder path is required"
            );
        }


        Project project =
                getProject(
                        projectId
                );


        WorkspaceFolderMetadata existing =
                folderMetadataRepository
                        .findByProjectIdAndPath(
                                projectId,
                                normalizedPath
                        )
                        .orElse(null);


        if (existing != null) {

            return new WorkspaceFolderResponse(
                    existing.getPath(),
                    existing.getName(),
                    existing.getParentPath()
            );
        }


        /*
         * Create parent folders first.
         */
        ensureParentFolders(
                project,
                normalizedPath
        );


        WorkspaceFolderMetadata folder =
                WorkspaceFolderMetadata.builder()
                        .project(project)
                        .path(normalizedPath)
                        .name(
                                extractName(
                                        normalizedPath
                                )
                        )
                        .parentPath(
                                extractParentPath(
                                        normalizedPath
                                )
                        )
                        .build();


        WorkspaceFolderMetadata saved =
                folderMetadataRepository.save(
                        folder
                );


        redisWorkspaceService.markActive(
                projectId
        );


        return new WorkspaceFolderResponse(
                saved.getPath(),
                saved.getName(),
                saved.getParentPath()
        );
    }


    // =====================================================
    // DELETE FOLDER
    // =====================================================

    @Transactional
    public void deleteFolder(
            Long projectId,
            String path,
            String username
    ) {

        requireProjectWriteAccess(
                projectId,
                username
        );


        String normalizedPath =
                normalizePath(
                        path
                );


        if (normalizedPath.isBlank()) {
            throw badRequest(
                    "Folder path is required"
            );
        }


        WorkspaceFolderMetadata folder =
                folderMetadataRepository
                        .findByProjectIdAndPath(
                                projectId,
                                normalizedPath
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Folder not found"
                                        )
                        );


        String prefix =
                normalizedPath + "/";


        boolean containsFiles =
                fileMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        )
                        .stream()
                        .anyMatch(
                                file ->
                                        file.getPath()
                                                .startsWith(
                                                        prefix
                                                )
                        );


        boolean containsFolders =
                folderMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        )
                        .stream()
                        .anyMatch(
                                current ->
                                        !current.getPath()
                                                .equals(
                                                        normalizedPath
                                                )
                                                &&
                                                current.getPath()
                                                        .startsWith(
                                                                prefix
                                                        )
                        );


        if (
                containsFiles ||
                        containsFolders
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Folder is not empty"
            );
        }


        folderMetadataRepository.delete(
                folder
        );


        redisWorkspaceService.markActive(
                projectId
        );
    }


    // =====================================================
    // COMPLETE OWNER IMPORT
    //
    // Incoming workspace becomes the complete source
    // of truth for this project.
    //
    // Missing files/folders are removed.
    // =====================================================

    @Transactional
    public WorkspaceSnapshotResponse importWorkspace(
            Long projectId,
            List<WorkspaceImportFile> incomingFiles,
            List<String> incomingFolders,
            String username
    ) {

        requireProjectWriteAccess(
                projectId,
                username
        );


        Project project =
                getProject(
                        projectId
                );


        List<WorkspaceImportFile> files =
                incomingFiles == null
                        ? List.of()
                        : incomingFiles;


        List<String> folders =
                incomingFolders == null
                        ? List.of()
                        : incomingFolders;


        /*
         * Normalize and deduplicate all incoming paths.
         */
        List<WorkspaceImportFile> normalizedFiles =
                normalizeImportFiles(
                        files
                );


        Set<String> incomingFilePaths =
                new LinkedHashSet<>();


        for (
                WorkspaceImportFile file
                : normalizedFiles
        ) {

            incomingFilePaths.add(
                    file.path()
            );
        }


        Set<String> incomingFolderPaths =
                new LinkedHashSet<>();


        for (
                String folder
                : folders
        ) {

            String normalized =
                    normalizePath(
                            folder
                    );


            if (
                    !normalized.isBlank()
            ) {

                addFolderWithParents(
                        incomingFolderPaths,
                        normalized
                );
            }
        }


        /*
         * Every file automatically implies all parent
         * folders.
         */
        for (
                String filePath
                : incomingFilePaths
        ) {

            for (
                    String parent
                    : getParentFolders(
                    filePath
            )
            ) {

                incomingFolderPaths.add(
                        parent
                );
            }
        }


        // =================================================
        // DELETE FILES THAT NO LONGER EXIST
        // =================================================

        List<WorkspaceFileMetadata> existingFiles =
                fileMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        );


        for (
                WorkspaceFileMetadata existing
                : existingFiles
        ) {

            if (
                    !incomingFilePaths.contains(
                            existing.getPath()
                    )
            ) {

                minioWorkspaceService.deleteFile(
                        projectId,
                        existing.getPath()
                );


                redisWorkspaceService.clearDirty(
                        projectId,
                        existing.getPath()
                );


                fileMetadataRepository.delete(
                        existing
                );
            }
        }


        // =================================================
        // DELETE FOLDERS THAT NO LONGER EXIST
        // =================================================

        List<WorkspaceFolderMetadata> existingFolders =
                folderMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        );


        existingFolders
                .stream()
                .filter(
                        existing ->
                                !incomingFolderPaths.contains(
                                        existing.getPath()
                                )
                )
                .sorted(
                        Comparator.comparingInt(
                                (WorkspaceFolderMetadata folder) ->
                                        depth(
                                                folder.getPath()
                                        )
                        ).reversed()
                )
                .forEach(
                        folderMetadataRepository::delete
                );


        // =================================================
        // CREATE / UPDATE FOLDERS
        // =================================================

        List<String> sortedFolders =
                incomingFolderPaths
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                        this::depth
                                )
                        )
                        .toList();


        for (
                String folderPath
                : sortedFolders
        ) {

            WorkspaceFolderMetadata existing =
                    folderMetadataRepository
                            .findByProjectIdAndPath(
                                    projectId,
                                    folderPath
                            )
                            .orElse(null);


            if (existing == null) {

                WorkspaceFolderMetadata folder =
                        WorkspaceFolderMetadata.builder()
                                .project(project)
                                .path(folderPath)
                                .name(
                                        extractName(
                                                folderPath
                                        )
                                )
                                .parentPath(
                                        extractParentPath(
                                                folderPath
                                        )
                                )
                                .build();


                folderMetadataRepository.save(
                        folder
                );

            } else {

                existing.setName(
                        extractName(
                                folderPath
                        )
                );

                existing.setParentPath(
                        extractParentPath(
                                folderPath
                        )
                );


                folderMetadataRepository.save(
                        existing
                );
            }
        }


        // =================================================
        // CREATE / UPDATE FILES
        // =================================================

        for (
                WorkspaceImportFile incoming
                : normalizedFiles
        ) {

            String path =
                    incoming.path();


            String content =
                    incoming.content() == null
                            ? ""
                            : incoming.content();


            /*
             * Ensure parent folder exists even if the
             * frontend did not explicitly send it.
             */
            ensureParentFolders(
                    project,
                    path
            );


            minioWorkspaceService.saveFile(
                    projectId,
                    path,
                    content
            );


            long size =
                    content.getBytes(
                            StandardCharsets.UTF_8
                    ).length;


            WorkspaceFileMetadata existing =
                    fileMetadataRepository
                            .findByProjectIdAndPath(
                                    projectId,
                                    path
                            )
                            .orElse(null);


            if (existing == null) {

                WorkspaceFileMetadata metadata =
                        WorkspaceFileMetadata.builder()
                                .project(project)
                                .path(path)
                                .name(
                                        incoming.name() == null ||
                                                incoming.name().isBlank()
                                                ? extractName(path)
                                                : incoming.name().trim()
                                )
                                .objectKey(
                                        minioWorkspaceService.buildObjectKey(
                                                projectId,
                                                path
                                        )
                                )
                                .size(size)
                                .version(1L)
                                .build();


                fileMetadataRepository.save(
                        metadata
                );

            } else {

                existing.setName(
                        incoming.name() == null ||
                                incoming.name().isBlank()
                                ? extractName(path)
                                : incoming.name().trim()
                );


                existing.setObjectKey(
                        minioWorkspaceService.buildObjectKey(
                                projectId,
                                path
                        )
                );


                existing.setSize(
                        size
                );


                existing.setVersion(
                        nextVersion(
                                existing.getVersion()
                        )
                );


                fileMetadataRepository.save(
                        existing
                );
            }


            /*
             * Imported content is already persisted.
             */
            redisWorkspaceService.clearDirty(
                    projectId,
                    path
            );
        }


        redisWorkspaceService.markActive(
                projectId
        );


        return getWorkspace(
                projectId,
                username
        );
    }


    // =====================================================
    // CLEAR COMPLETE PROJECT WORKSPACE
    // =====================================================

    @Transactional
    public void clearProjectWorkspace(
            Long projectId
    ) {

        if (projectId == null) {
            return;
        }


        List<WorkspaceFileMetadata> files =
                fileMetadataRepository
                        .findByProjectIdOrderByPathAsc(
                                projectId
                        );


        for (
                WorkspaceFileMetadata file
                : files
        ) {

            try {

                minioWorkspaceService.deleteFile(
                        projectId,
                        file.getPath()
                );

            } catch (
                    Exception exception
            ) {

                System.err.println(
                        "[WORKSPACE CLEAR] MinIO delete failed: " +
                                "Project=" + projectId +
                                " Path=" + file.getPath() +
                                " Error=" + exception.getMessage()
                );
            }


            redisWorkspaceService.clearDirty(
                    projectId,
                    file.getPath()
            );
        }


        fileMetadataRepository.deleteByProjectId(
                projectId
        );


        folderMetadataRepository.deleteByProjectId(
                projectId
        );


        redisWorkspaceService.clear(
                projectId
        );
    }


    // =====================================================
    // ENSURE PARENT FOLDERS
    // =====================================================

    private void ensureParentFolders(
            Project project,
            String fileOrFolderPath
    ) {

        for (
                String parent
                : getParentFolders(
                fileOrFolderPath
        )
        ) {

            createFolderInternal(
                    project,
                    parent
            );
        }
    }


    // =====================================================
    // CREATE FOLDER INTERNAL
    // =====================================================

    private void createFolderInternal(
            Project project,
            String path
    ) {

        if (
                path == null ||
                        path.isBlank()
        ) {
            return;
        }


        String normalized =
                normalizePath(
                        path
                );


        if (normalized.isBlank()) {
            return;
        }


        if (
                folderMetadataRepository
                        .existsByProjectIdAndPath(
                                project.getId(),
                                normalized
                        )
        ) {
            return;
        }


        /*
         * Make sure parent exists first.
         */
        String parent =
                extractParentPath(
                        normalized
                );


        if (
                parent != null &&
                        !parent.isBlank()
        ) {

            createFolderInternal(
                    project,
                    parent
            );
        }


        WorkspaceFolderMetadata folder =
                WorkspaceFolderMetadata.builder()
                        .project(project)
                        .path(normalized)
                        .name(
                                extractName(
                                        normalized
                                )
                        )
                        .parentPath(parent)
                        .build();


        folderMetadataRepository.save(
                folder
        );
    }


    // =====================================================
    // NORMALIZE IMPORT FILES
    // =====================================================

    private List<WorkspaceImportFile> normalizeImportFiles(
            List<WorkspaceImportFile> files
    ) {

        List<WorkspaceImportFile> result =
                new ArrayList<>();


        Set<String> seen =
                new HashSet<>();


        for (
                WorkspaceImportFile file
                : files
        ) {

            if (file == null) {
                continue;
            }


            String path =
                    normalizePath(
                            file.path()
                    );


            if (path.isBlank()) {
                continue;
            }


            if (
                    !seen.add(path)
            ) {

                continue;
            }


            String name =
                    file.name();


            if (
                    name == null ||
                            name.isBlank()
            ) {

                name =
                        extractName(
                                path
                        );

            } else {

                name =
                        name.trim();
            }


            String content =
                    file.content() == null
                            ? ""
                            : file.content();


            result.add(
                    new WorkspaceImportFile(
                            path,
                            name,
                            content
                    )
            );
        }


        return result;
    }


    // =====================================================
    // ADD FOLDER + PARENTS
    // =====================================================

    private void addFolderWithParents(
            Set<String> folders,
            String path
    ) {

        List<String> parents =
                getParentFolders(
                        path
                );


        folders.addAll(
                parents
        );


        folders.add(
                path
        );
    }


    // =====================================================
    // GET PARENT FOLDERS
    // =====================================================

    private List<String> getParentFolders(
            String path
    ) {

        String normalized =
                normalizePath(
                        path
                );


        if (normalized.isBlank()) {
            return List.of();
        }


        String[] parts =
                normalized
                        .split("/");


        if (parts.length <= 1) {
            return List.of();
        }


        List<String> result =
                new ArrayList<>();


        String current = "";


        for (
                int index = 0;
                index < parts.length - 1;
                index++
        ) {

            current =
                    current.isBlank()
                            ? parts[index]
                            : current +
                              "/" +
                              parts[index];


            result.add(
                    current
            );
        }


        return result;
    }


    // =====================================================
    // EXTRACT NAME
    // =====================================================

    private String extractName(
            String path
    ) {

        String normalized =
                normalizePath(
                        path
                );


        if (normalized.isBlank()) {
            return "";
        }


        int index =
                normalized.lastIndexOf("/");


        if (index < 0) {
            return normalized;
        }


        return normalized.substring(
                index + 1
        );
    }


    // =====================================================
    // EXTRACT PARENT
    // =====================================================

    private String extractParentPath(
            String path
    ) {

        String normalized =
                normalizePath(
                        path
                );


        if (normalized.isBlank()) {
            return null;
        }


        int index =
                normalized.lastIndexOf("/");


        if (index < 0) {
            return null;
        }


        String parent =
                normalized.substring(
                        0,
                        index
                );


        return parent.isBlank()
                ? null
                : parent;
    }


    // =====================================================
    // DEPTH
    // =====================================================

    private int depth(
            String path
    ) {

        String normalized =
                normalizePath(
                        path
                );


        if (normalized.isBlank()) {
            return 0;
        }


        return normalized
                .split("/")
                .length;
    }


    // =====================================================
    // VERSION
    // =====================================================

    private long nextVersion(
            Long currentVersion
    ) {

        if (
                currentVersion == null ||
                        currentVersion < 1
        ) {
            return 1L;
        }


        return currentVersion + 1L;
    }


    // =====================================================
    // PROJECT
    // =====================================================

    private Project getProject(
            Long projectId
    ) {

        /*
         * ProjectMemberService already owns access-control
         * logic, but WorkspaceService needs the Project
         * entity for metadata relations.
         *
         * We obtain it through the member service.
         */
        return projectMemberService
                .getProject(
                        projectId
                );
    }


    // =====================================================
    // READ ACCESS
    // =====================================================

    private void requireProjectReadAccess(
            Long projectId,
            String username
    ) {

        if (
                projectId == null
        ) {

            throw badRequest(
                    "Project ID is required"
            );
        }


        if (
                username == null ||
                        username.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }


        projectMemberService.requireReadAccess(
                projectId,
                username
        );
    }


    // =====================================================
    // WRITE ACCESS
    // =====================================================

    private void requireProjectWriteAccess(
            Long projectId,
            String username
    ) {

        if (
                projectId == null
        ) {

            throw badRequest(
                    "Project ID is required"
            );
        }


        if (
                username == null ||
                        username.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }


        projectMemberService.requireWriteAccess(
                projectId,
                username
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
                normalized.isBlank()
        ) {
            return "";
        }


        if (
                normalized.contains("..") ||
                        normalized.contains("//")
        ) {

            throw badRequest(
                    "Invalid workspace path"
            );
        }


        return normalized;
    }


    // =====================================================
    // BAD REQUEST
    // =====================================================

    private ResponseStatusException badRequest(
            String message
    ) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }


    // =====================================================
    // DTOs
    // =====================================================

    public record WorkspaceImportFile(
            String path,
            String name,
            String content
    ) {}


    public record WorkspaceSnapshotResponse(
            Long projectId,
            List<WorkspaceFileResponse> files,
            List<WorkspaceFolderResponse> folders
    ) {}


    public record WorkspaceFileResponse(
            String path,
            String name,
            Long size,
            Long version
    ) {}


    public record WorkspaceFolderResponse(
            String path,
            String name,
            String parentPath
    ) {}
}