package com.codebreak.backend.file;

import com.codebreak.backend.project.Project;
import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectFileService {

    private final ProjectFileRepository fileRepository;

    private final ProjectRepository projectRepository;

    private final ProjectMemberService projectMemberService;


    // =========================================
    // CREATE FILE
    // =========================================

    public ProjectFile createFile(
            Long projectId,
            ProjectFileRequest request,
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
                request.name() == null ||
                        request.name().isBlank()
        ) {

            throw new RuntimeException(
                    "File name is required"
            );
        }


        if (
                request.path() == null ||
                        request.path().isBlank()
        ) {

            throw new RuntimeException(
                    "File path is required"
            );
        }


        String path =
                normalizePath(
                        request.path()
                );


        if (
                fileRepository
                        .findByProjectIdAndPath(
                                projectId,
                                path
                        )
                        .isPresent()
        ) {

            throw new RuntimeException(
                    "A file already exists at this path"
            );
        }


        ProjectFile file =
                ProjectFile.builder()

                        .name(
                                request.name()
                                        .trim()
                        )

                        .path(path)

                        .content(
                                request.content() == null
                                        ? ""
                                        : request.content()
                        )

                        .project(project)

                        .build();


        return fileRepository.save(file);
    }


    // =========================================
    // IMPORT PROJECT FILES
    // =========================================

    public List<ProjectFile> importFiles(
            Long projectId,
            ProjectImportRequest request,
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
                request == null ||
                        request.files() == null
        ) {

            throw new RuntimeException(
                    "No files provided"
            );
        }


        List<ProjectFile> importedFiles =
                request.files()
                        .stream()
                        .map(importFile -> {

                            if (
                                    importFile.path() == null ||
                                            importFile.path().isBlank()
                            ) {

                                throw new RuntimeException(
                                        "Invalid file path"
                                );
                            }


                            String path =
                                    normalizePath(
                                            importFile.path()
                                    );


                            // -------------------------
                            // EXISTING FILE
                            // -------------------------

                            ProjectFile file =
                                    fileRepository
                                            .findByProjectIdAndPath(
                                                    projectId,
                                                    path
                                            )
                                            .orElse(null);


                            if (file != null) {

                                file.setName(
                                        importFile.name()
                                );

                                file.setContent(
                                        importFile.content() == null
                                                ? ""
                                                : importFile.content()
                                );

                                return file;
                            }


                            // -------------------------
                            // NEW FILE
                            // -------------------------

                            return ProjectFile.builder()

                                    .name(
                                            importFile.name()
                                    )

                                    .path(path)

                                    .content(
                                            importFile.content() == null
                                                    ? ""
                                                    : importFile.content()
                                    )

                                    .project(project)

                                    .build();

                        })
                        .toList();


        return fileRepository.saveAll(
                importedFiles
        );
    }


    // =========================================
    // GET FILES
    // =========================================

    public List<ProjectFile> getProjectFiles(
            Long projectId,
            String username
    ) {

        projectMemberService.requireReadAccess(
                projectId,
                username
        );


        return fileRepository
                .findByProjectId(
                        projectId
                );
    }


    // =========================================
    // UPDATE FILE
    // =========================================

    public ProjectFile updateFile(
            Long projectId,
            Long fileId,
            String content,
            String username
    ) {

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        ProjectFile file =
                fileRepository
                        .findById(fileId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "File not found"
                                )
                        );


        if (
                !file.getProject()
                        .getId()
                        .equals(projectId)
        ) {

            throw new RuntimeException(
                    "File does not belong to this project"
            );
        }


        file.setContent(
                content
        );


        return fileRepository.save(
                file
        );
    }


    // =========================================
    // DELETE FILE
    // =========================================

    public void deleteFile(
            Long projectId,
            Long fileId,
            String username
    ) {

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );


        ProjectFile file =
                fileRepository
                        .findById(fileId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "File not found"
                                )
                        );


        if (
                !file.getProject()
                        .getId()
                        .equals(projectId)
        ) {

            throw new RuntimeException(
                    "File does not belong to this project"
            );
        }


        fileRepository.delete(file);
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
                normalized.isBlank() ||
                        normalized.contains("..")
        ) {

            throw new RuntimeException(
                    "Invalid file path"
            );
        }


        return normalized;
    }
}