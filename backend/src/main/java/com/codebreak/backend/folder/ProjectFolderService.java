package com.codebreak.backend.folder;

import com.codebreak.backend.project.Project;
import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectFolderService {

    private final ProjectFolderRepository folderRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberService projectMemberService;


    // =========================================
    // CREATE FOLDER
    // =========================================

    public ProjectFolderResponse createFolder(

            Long projectId,

            ProjectFolderRequest request,

            String username

    ) {

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Folder details are required"
            );
        }

        if (
                request.name() == null ||
                        request.name().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Folder name is required"
            );
        }


        if (
                request.path() == null ||
                        request.path().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Folder path is required"
            );
        }


        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Project not found"
                                )
                        );


        String path =
                normalizePath(
                        request.path()
                );


        // =====================================
        // DUPLICATE CHECK
        // =====================================

        if (
                folderRepository
                        .existsByProjectIdAndPath(
                                projectId,
                                path
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A folder already exists at this path"
            );
        }


        // =====================================
        // PARENT
        // =====================================

        ProjectFolder parent = null;


        if (
                request.parentId() != null
        ) {

            parent =
                    folderRepository
                            .findById(
                                    request.parentId()
                            )
                            .orElseThrow(() ->
                                    new ResponseStatusException(
                                            HttpStatus.NOT_FOUND,
                                            "Parent folder not found"
                                    )
                            );


            if (
                    !parent.getProject()
                            .getId()
                            .equals(projectId)
            ) {

                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Parent folder not found in this project"
                );
            }
        }


        // =====================================
        // CREATE
        // =====================================

        ProjectFolder folder =
                ProjectFolder.builder()

                        .name(
                                request.name()
                                        .trim()
                        )

                        .path(path)

                        .project(project)

                        .parent(parent)

                        .build();


        ProjectFolder saved =
                folderRepository.save(
                        folder
                );


        return toResponse(saved);
    }


    // =========================================
    // GET FOLDERS
    // =========================================

    public List<ProjectFolderResponse>
    getProjectFolders(

            Long projectId,

            String username

    ) {

        projectMemberService.requireReadAccess(
                projectId,
                username
        );


        return folderRepository
                .findByProjectId(projectId)

                .stream()

                .map(
                        this::toResponse
                )

                .toList();
    }


    // =========================================
    // DELETE FOLDER
    // =========================================

    public void deleteFolder(

            Long projectId,

            Long folderId,

            String username

    ) {

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        ProjectFolder folder =
                folderRepository
                        .findById(folderId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Folder not found"
                                )
                        );


        if (
                !folder.getProject()
                        .getId()
                        .equals(projectId)
        ) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Folder not found in this project"
            );
        }


        // =====================================
        // CHECK CHILD FOLDERS
        // =====================================

        if (
                !folderRepository
                        .findByParentId(
                                folderId
                        )
                        .isEmpty()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Folder contains child folders"
            );
        }


        folderRepository.delete(
                folder
        );
    }


    // =========================================
    // RESPONSE
    // =========================================

    private ProjectFolderResponse toResponse(
            ProjectFolder folder
    ) {

        return new ProjectFolderResponse(

                folder.getId(),

                folder.getName(),

                folder.getPath(),

                folder.getParent() != null
                        ? folder.getParent().getId()
                        : null

        );
    }


    // =========================================
    // NORMALIZE PATH
    // =========================================

    private String normalizePath(
            String path
    ) {
        if (path == null || path.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Folder path is required"
            );
        }

        String normalized = path.trim()
                .replace("\\", "/")
                .replaceAll("/+", "/");

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isBlank() || normalized.length() > 1000) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid folder path"
            );
        }

        boolean containsTraversalSegment = java.util.Arrays.stream(
                normalized.split("/")
        ).anyMatch(segment -> segment.equals(".") || segment.equals(".."));

        if (containsTraversalSegment) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Invalid folder path"
            );
        }

        return normalized;
    }
    public List<ProjectFolderResponse> importFolders(

            Long projectId,

            List<String> paths,

            String username

    ) {

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        if (paths == null || paths.isEmpty()) {
            return List.of();
        }

        List<String> normalizedPaths = paths.stream()
                .map(this::normalizePath)
                .distinct()
                .sorted()
                .toList();

        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Project not found"
                                )
                        );

        return normalizedPaths
                .stream()
                .map(path -> {

                    ProjectFolder existing =
                            folderRepository
                                    .findByProjectIdAndPath(
                                            projectId,
                                            path
                                    )
                                    .orElse(null);


                    if (
                            existing != null
                    ) {

                        return existing;

                    }


                    String[] parts =
                            path.split("/");


                    String name =
                            parts[parts.length - 1];


                    String parentPath =
                            path.contains("/")
                                    ? path.substring(
                                    0,
                                    path.lastIndexOf("/")
                            )
                                    : "";


                    ProjectFolder parent =
                            parentPath.isBlank()
                                    ? null
                                    : folderRepository
                                      .findByProjectIdAndPath(
                                              projectId,
                                              parentPath
                                      )
                                      .orElse(null);


                    ProjectFolder folder =
                            ProjectFolder.builder()

                                    .name(name)

                                    .path(path)

                                    .project(project)

                                    .parent(parent)

                                    .build();


                    return folderRepository.save(
                            folder
                    );

                })

                .map(this::toResponse)

                .toList();
    }
}