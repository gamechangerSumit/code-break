package com.codebreak.backend.file;

import com.codebreak.backend.project.Project;
import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectFileService {

    private static final int MAX_FILE_CONTENT_BYTES = 5 * 1024 * 1024;
    private static final long MAX_IMPORT_TOTAL_CONTENT_BYTES = 20L * 1024 * 1024;

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

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File details are required"
            );
        }

        validateContent(request.content());

        if (
                request.name() == null ||
                        request.name().isBlank() ||
                        request.name().length() > 255
        ) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File name must be between 1 and 255 characters"
            );
        }


        if (
                request.path() == null ||
                        request.path().isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File path is required"
            );
        }


        String path =
                normalizePath(
                        request.path()
                );


        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Project not found"
                                )
                        );


        if (
                fileRepository
                        .findByProjectIdAndPath(
                                projectId,
                                path
                        )
                        .isPresent()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
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

    @Transactional
    public List<ProjectFile> importFiles(
            Long projectId,
            ProjectImportRequest request,
            String username
    ) {

        projectMemberService.requireWriteAccess(
                projectId,
                username
        );

        validateImportRequest(request);

        Project project =
                projectRepository
                        .findById(projectId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Project not found"
                                )
                        );


        if (
                request == null ||
                        request.files() == null
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
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

                                throw new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
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

        validateContent(content);

        ProjectFile file =
                fileRepository
                        .findById(fileId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "File not found"
                                )
                        );


        if (
                !file.getProject()
                        .getId()
                        .equals(projectId)
        ) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "File not found in this project"
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
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "File not found"
                                )
                        );


        if (
                !file.getProject()
                        .getId()
                        .equals(projectId)
        ) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "File not found in this project"
            );
        }


        fileRepository.delete(file);
    }



    private void validateContent(String content) {
        if (content != null
                && content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_CONTENT_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File content exceeds the 5 MiB limit"
            );
        }
    }

    private void validateImportRequest(ProjectImportRequest request) {
        if (request == null || request.files() == null || request.files().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one file is required for import"
            );
        }

        if (request.files().size() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Project import contains too many files"
            );
        }

        if (request.folders() != null && request.folders().size() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Project import contains too many folders"
            );
        }

        long totalBytes = 0;
        for (ProjectImportFile file : request.files()) {
            if (file == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Project import contains an invalid file"
                );
            }
            if (file.path() == null || file.path().isBlank() || file.path().length() > 1000) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Project import contains an invalid file path"
                );
            }
            if (file.name() == null || file.name().isBlank()
                    || file.name().length() > 255) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Imported file name must be between 1 and 255 characters"
                );
            }
            String content = file.content() == null ? "" : file.content();
            int contentBytes = content.getBytes(StandardCharsets.UTF_8).length;
            if (contentBytes > MAX_FILE_CONTENT_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Imported file content exceeds the 5 MiB limit"
                );
            }
            totalBytes += contentBytes;
            if (totalBytes > MAX_IMPORT_TOTAL_CONTENT_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Project import content exceeds the 20 MiB total limit"
                );
            }
        }
    }

    // =========================================
    // NORMALIZE PATH
    // =========================================

    private String normalizePath(
            String path
    ) {

        if (path == null || path.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
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

        boolean containsTraversalSegment = java.util.Arrays.stream(
                normalized.split("/")
        ).anyMatch(segment -> segment.equals(".") || segment.equals(".."));

        if (normalized.isBlank() || normalized.length() > 1000 || containsTraversalSegment) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
            );
        }

        return normalized;
    }
}