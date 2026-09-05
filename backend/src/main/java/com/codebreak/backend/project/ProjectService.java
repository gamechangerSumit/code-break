package com.codebreak.backend.project;

import com.codebreak.backend.file.ProjectFileRepository;
import com.codebreak.backend.folder.ProjectFolderRepository;
import com.codebreak.backend.user.User;
import com.codebreak.backend.user.UserRepository;
import com.codebreak.backend.workspace.RedisWorkspaceService;
import com.codebreak.backend.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;

    private final UserRepository userRepository;

    private final ProjectMemberRepository projectMemberRepository;

    private final ProjectFileRepository projectFileRepository;

    private final ProjectFolderRepository projectFolderRepository;

    private final WorkspaceService workspaceService;

    private final RedisWorkspaceService redisWorkspaceService;


    // =========================================================
    // CREATE PROJECT
    // =========================================================

    public ProjectResponse createProject(
            ProjectRequest request,
            String username
    ) {

        User owner =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        if (
                request == null ||
                        request.name() == null ||
                        request.name().isBlank()
        ) {

            throw new RuntimeException(
                    "Project name is required"
            );
        }

        Project project =
                Project.builder()
                        .name(
                                request.name().trim()
                        )
                        .description(
                                request.description()
                        )
                        .owner(owner)
                        .build();

        Project savedProject =
                projectRepository.save(
                        project
                );


        // =====================================================
        // ADD OWNER AS MEMBER
        // =====================================================

        ProjectMember ownerMember =
                ProjectMember.builder()
                        .project(savedProject)
                        .user(owner)
                        .role(ProjectRole.OWNER)
                        .build();

        projectMemberRepository.save(
                ownerMember
        );


        return new ProjectResponse(
                savedProject.getId(),
                savedProject.getName(),
                savedProject.getDescription(),
                savedProject.getJoinCode()
        );
    }


    // =========================================================
    // GET USER PROJECTS
    // =========================================================

    public List<ProjectResponse> getUserProjects(
            String username
    ) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );


        // =====================================================
        // OWNED PROJECTS
        // =====================================================

        List<Project> ownedProjects =
                projectRepository
                        .findByOwnerId(
                                user.getId()
                        );


        // =====================================================
        // MEMBER PROJECTS
        // =====================================================

        List<Project> memberProjects =
                projectMemberRepository
                        .findByUserId(
                                user.getId()
                        )
                        .stream()
                        .map(
                                ProjectMember::getProject
                        )
                        .toList();


        // =====================================================
        // COMBINE WITHOUT DUPLICATES
        // =====================================================

        Map<Long, Project> projects =
                new LinkedHashMap<>();

        for (Project project :
                ownedProjects) {

            projects.put(
                    project.getId(),
                    project
            );
        }

        for (Project project :
                memberProjects) {

            projects.put(
                    project.getId(),
                    project
            );
        }


        // =====================================================
        // RESPONSE
        // =====================================================

        return projects
                .values()
                .stream()
                .map(project -> {

                    boolean isOwner =
                            project.getOwner()
                                    .getId()
                                    .equals(
                                            user.getId()
                                    );

                    return new ProjectResponse(
                            project.getId(),
                            project.getName(),
                            project.getDescription(),
                            isOwner
                                    ? project.getJoinCode()
                                    : null
                    );
                })
                .toList();
    }


    // =========================================================
    // DELETE PROJECT
    // OWNER ONLY
    // =========================================================

    @Transactional
    public void deleteProject(
            Long projectId,
            String username
    ) {

        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "User not found"
                                )
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


        // =====================================================
        // OWNER CHECK
        // =====================================================

        if (
                project.getOwner() == null ||
                        project.getOwner().getId() == null ||
                        !project.getOwner()
                                .getId()
                                .equals(
                                        user.getId()
                                )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the project owner can delete this project."
            );
        }


        // =====================================================
        // CLEAR NEW WORKSPACE
        // =====================================================
        //
        // MinIO:
        //   projects/{projectId}/files/*
        //
        // PostgreSQL:
        //   workspace_files
        //   workspace_folders
        //
        // Redis:
        //   active + dirty state
        //
        // =====================================================

        workspaceService.clearProjectWorkspace(
                projectId
        );


        // =====================================================
        // CLEAR LEGACY FILE DATA
        // =====================================================
        //
        // These old tables are still present in the project.
        // We are not deleting their entities yet because old
        // APIs may still exist.
        //
        // But before deleting the Project itself, their rows
        // must be removed to avoid FK violations.
        //
        // =====================================================

        projectFileRepository
                .findByProjectId(projectId)
                .forEach(
                        projectFileRepository::delete
                );


        // =====================================================
        // CLEAR LEGACY FOLDER DATA
        // =====================================================
        //
        // Child folders have parent_id FK, so delete children
        // before parents.
        //
        // =====================================================

        List<com.codebreak.backend.folder.ProjectFolder>
                legacyFolders =
                projectFolderRepository
                        .findByProjectId(
                                projectId
                        );

        legacyFolders
                .stream()
                .sorted(
                        (first, second) ->
                                Integer.compare(
                                        depth(second.getPath()),
                                        depth(first.getPath())
                                )
                )
                .forEach(
                        projectFolderRepository::delete
                );


        // =====================================================
        // DELETE ALL MEMBERS
        // =====================================================

        projectMemberRepository
                .deleteByProjectId(
                        projectId
                );


        // =====================================================
        // FINAL PROJECT DELETE
        // =====================================================

        projectRepository.delete(
                project
        );


        // =====================================================
        // REDIS FINAL CLEANUP
        // =====================================================

        redisWorkspaceService.clear(
                projectId
        );
    }


    // =========================================================
    // PATH DEPTH
    // =========================================================

    private int depth(
            String path
    ) {

        if (
                path == null ||
                        path.isBlank()
        ) {
            return 0;
        }

        return path
                .replace("\\", "/")
                .split("/")
                .length;
    }
}