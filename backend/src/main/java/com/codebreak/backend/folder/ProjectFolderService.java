package com.codebreak.backend.folder;

import com.codebreak.backend.project.Project;
import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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


        if (
                request.name() == null ||
                        request.name().isBlank()
        ) {

            throw new RuntimeException(
                    "Folder name is required"
            );
        }


        if (
                request.path() == null ||
                        request.path().isBlank()
        ) {

            throw new RuntimeException(
                    "Folder path is required"
            );
        }


        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
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

            throw new RuntimeException(
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
                                    new RuntimeException(
                                            "Parent folder not found"
                                    )
                            );


            if (
                    !parent.getProject()
                            .getId()
                            .equals(projectId)
            ) {

                throw new RuntimeException(
                        "Parent folder does not belong to this project"
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
                                new RuntimeException(
                                        "Folder not found"
                                )
                        );


        if (
                !folder.getProject()
                        .getId()
                        .equals(projectId)
        ) {

            throw new RuntimeException(
                    "Folder does not belong to this project"
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

            throw new RuntimeException(
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

        String normalized =
                path.trim()
                        .replace("\\", "/");


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
                normalized.contains("..")
        ) {

            throw new RuntimeException(
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


        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Project not found"
                                )
                        );


        if (
                paths == null ||
                        paths.isEmpty()
        ) {

            return List.of();
        }


        return paths
                .stream()

                .map(path ->
                        path
                                .trim()
                                .replace("\\", "/")
                )

                .filter(path ->
                        !path.isBlank()
                )

                .distinct()

                .sorted()

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