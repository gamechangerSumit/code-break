package com.codebreak.backend.project;

import com.codebreak.backend.user.User;
import com.codebreak.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SimpMessagingTemplate messagingTemplate;


    // =====================================================
    // GET USER
    // =====================================================

    private User getUser(String username) {

        return userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "User not found"
                        )
                );
    }


    // =====================================================
    // GET PROJECT
    // =====================================================

    public Project getProject(Long projectId) {

        if (projectId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Project ID is required"
            );
        }

        return projectRepository
                .findById(projectId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Project not found"
                        )
                );
    }


    // =====================================================
    // GET PROJECT MEMBER
    // =====================================================

    public ProjectMember getProjectMember(
            Long projectId,
            String username
    ) {

        User user = getUser(username);

        return projectMemberRepository
                .findByProjectIdAndUserId(
                        projectId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You are not a member of this project"
                        )
                );
    }


    // =====================================================
    // READ ACCESS
    // =====================================================

    public void requireReadAccess(
            Long projectId,
            String username
    ) {

        getProjectMember(
                projectId,
                username
        );
    }


    // =====================================================
    // WRITE ACCESS
    // =====================================================

    public void requireWriteAccess(
            Long projectId,
            String username
    ) {

        ProjectMember member =
                getProjectMember(
                        projectId,
                        username
                );

        ProjectRole role = member.getRole();

        if (
                role != ProjectRole.OWNER &&
                        role != ProjectRole.EDITOR
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You do not have permission to modify this project"
            );
        }
    }


    // =====================================================
    // JOIN PROJECT
    // =====================================================

    public ProjectMemberResponse joinProject(
            String joinCode,
            String username
    ) {

        if (
                joinCode == null ||
                        joinCode.isBlank()
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Join code is required"
            );
        }

        User user = getUser(username);

        String normalizedCode =
                joinCode
                        .trim()
                        .toUpperCase();

        Project project =
                projectRepository
                        .findByJoinCode(normalizedCode)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Invalid join code"
                                )
                        );


        // =============================================
        // OWNER CANNOT JOIN OWN PROJECT
        // =============================================

        if (
                project.getOwner() != null &&
                        project.getOwner()
                                .getId()
                                .equals(user.getId())
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You are already the owner of this project"
            );
        }


        // =============================================
        // ALREADY MEMBER
        // =============================================

        if (
                projectMemberRepository
                        .existsByProjectIdAndUserId(
                                project.getId(),
                                user.getId()
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You are already a member of this project"
            );
        }


        // =============================================
        // NEW MEMBER = VIEWER
        // =============================================

        ProjectMember member =
                ProjectMember.builder()
                        .project(project)
                        .user(user)
                        .role(ProjectRole.VIEWER)
                        .build();

        ProjectMember saved =
                projectMemberRepository.save(member);


        return toResponse(saved);
    }


    // =====================================================
    // GET PROJECT MEMBERS
    // =====================================================

    public List<ProjectMemberResponse> getProjectMembers(
            Long projectId,
            String username
    ) {

        requireReadAccess(
                projectId,
                username
        );

        return projectMemberRepository
                .findByProjectId(projectId)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    // =====================================================
    // CHANGE MEMBER ROLE
    // =====================================================

    public ProjectMemberResponse changeRole(
            Long projectId,
            Long memberId,
            ProjectRole newRole,
            String username
    ) {

        if (newRole == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Role is required"
            );
        }


        ProjectMember requester =
                getProjectMember(
                        projectId,
                        username
                );


        if (
                requester.getRole() !=
                        ProjectRole.OWNER
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only the project owner can change member roles"
            );
        }


        ProjectMember member =
                projectMemberRepository
                        .findById(memberId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Project member not found"
                                )
                        );


        if (
                member.getProject() == null ||
                        member.getProject().getId() == null ||
                        !member.getProject()
                                .getId()
                                .equals(projectId)
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Member does not belong to this project"
            );
        }


        // =============================================
        // OWNER ROLE CANNOT BE REMOVED
        // =============================================

        if (
                member.getUser() != null &&
                        member.getProject().getOwner() != null &&
                        member.getUser().getId()
                                .equals(
                                        member.getProject()
                                                .getOwner()
                                                .getId()
                                ) &&
                        newRole != ProjectRole.OWNER
        ) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Project owner's role cannot be changed"
            );
        }


        member.setRole(newRole);

        ProjectMember saved =
                projectMemberRepository.save(member);


        ProjectMemberResponse response =
                toResponse(saved);


        // =============================================
        // BROADCAST ROLE CHANGE
        // =============================================

        messagingTemplate.convertAndSend(
                "/topic/project/" +
                        projectId +
                        "/members",
                new RoleChangedMessage(
                        "ROLE_CHANGED",
                        projectId,
                        saved.getId(),
                        saved.getUser().getUsername(),
                        saved.getRole()
                )
        );


        return response;
    }


    // =====================================================
    // RESPONSE MAPPER
    // =====================================================

    private ProjectMemberResponse toResponse(
            ProjectMember member
    ) {

        return new ProjectMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getUsername(),
                member.getRole()
        );
    }


    // =====================================================
    // ROLE CHANGE MESSAGE
    // =====================================================

    public record RoleChangedMessage(
            String type,
            Long projectId,
            Long memberId,
            String username,
            ProjectRole role
    ) {
    }
}