package com.codebreak.backend.invitation;

import com.codebreak.backend.activity.ProjectActivityService;
import com.codebreak.backend.project.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class ProjectInvitationService {
    private final ProjectInvitationRepository invitations;
    private final ProjectMemberService members;
    private final ProjectRepository projects;
    private final ProjectActivityService activity;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public InvitationResponse create(Long projectId, String username) {
        ProjectMember member = members.getProjectMember(projectId, username);
        if (member.getRole() != ProjectRole.OWNER && member.getRole() != ProjectRole.EDITOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only owners and editors can invite members");
        }
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        ProjectInvitation invite = invitations.save(ProjectInvitation.builder()
                .projectId(projectId).token(token).createdBy(username)
                .expiresAt(LocalDateTime.now().plusDays(7)).build());
        activity.record(projectId, username, "INVITATION_CREATED", "Created a project invitation");
        return new InvitationResponse(invite.getToken(), invite.getExpiresAt());
    }

    @Transactional
    public ProjectMemberResponse accept(String token, String username) {
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invitation token is required");
        }
        ProjectInvitation invite = invitations.findByToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found"));
        if (invite.getAcceptedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invitation has already been used");
        }
        if (invite.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invitation has expired");
        }
        Project project = projects.findById(invite.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        ProjectMemberResponse response = members.joinProject(project.getJoinCode(), username);
        invite.setAcceptedAt(LocalDateTime.now());
        activity.record(project.getId(), username, "MEMBER_JOINED", "Joined the project using an invitation");
        return response;
    }

    public record InvitationResponse(String token, LocalDateTime expiresAt) {}
}
