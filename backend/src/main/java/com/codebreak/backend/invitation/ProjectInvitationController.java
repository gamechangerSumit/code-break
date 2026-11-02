package com.codebreak.backend.invitation;

import com.codebreak.backend.project.ProjectMemberResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProjectInvitationController {
    private final ProjectInvitationService service;

    @PostMapping("/projects/{projectId}/invitations")
    public ResponseEntity<ProjectInvitationService.InvitationResponse> create(
            @PathVariable Long projectId, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, auth.getName()));
    }

    @PostMapping("/invitations/accept")
    public ProjectMemberResponse accept(@RequestBody AcceptInvitationRequest request, Authentication auth) {
        return service.accept(request.token(), auth.getName());
    }

    public record AcceptInvitationRequest(String token) {}
}
