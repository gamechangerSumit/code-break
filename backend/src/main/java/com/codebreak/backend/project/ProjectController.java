package com.codebreak.backend.project;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;

    // =========================================
    // CREATE PROJECT
    // =========================================

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @RequestBody ProjectRequest request,
            Authentication authentication
    ) {

        ProjectResponse project =
                projectService.createProject(
                        request,
                        authentication.getName()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(project);
    }

    // =========================================
    // GET USER PROJECTS
    // =========================================

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getUserProjects(
            Authentication authentication
    ) {

        List<ProjectResponse> projects =
                projectService.getUserProjects(
                        authentication.getName()
                );

        return ResponseEntity.ok(projects);
    }

    // =========================================
    // JOIN PROJECT
    // =========================================

    @PostMapping("/join")
    public ResponseEntity<ProjectMemberResponse> joinProject(
            @RequestBody JoinProjectRequest request,
            Authentication authentication
    ) {

        ProjectMemberResponse member =
                projectMemberService.joinProject(
                        request.joinCode(),
                        authentication.getName()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(member);
    }

    // =========================================
    // GET PROJECT MEMBERS
    // =========================================

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> getProjectMembers(
            @PathVariable Long projectId,
            Authentication authentication
    ) {

        List<ProjectMemberResponse> members =
                projectMemberService.getProjectMembers(
                        projectId,
                        authentication.getName()
                );

        return ResponseEntity.ok(members);
    }

    // =========================================
    // CHANGE MEMBER ROLE
    // OWNER ONLY
    // =========================================

    @PutMapping("/{projectId}/members/{memberId}/role")
    public ResponseEntity<ProjectMemberResponse> changeMemberRole(
            @PathVariable Long projectId,
            @PathVariable Long memberId,
            @RequestParam ProjectRole role,
            Authentication authentication
    ) {

        ProjectMemberResponse member =
                projectMemberService.changeRole(
                        projectId,
                        memberId,
                        role,
                        authentication.getName()
                );

        return ResponseEntity.ok(member);
    }

    // =========================================
    // DELETE PROJECT
    // OWNER ONLY
    // =========================================

    @DeleteMapping("/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @PathVariable Long projectId,
            Authentication authentication
    ) {

        projectService.deleteProject(
                projectId,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }
}