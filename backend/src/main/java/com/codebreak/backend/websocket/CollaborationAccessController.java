package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectMember;
import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/collaboration-access")
@RequiredArgsConstructor
public class CollaborationAccessController {
    private final ProjectMemberService members;

    @GetMapping
    public CollaborationAccessResponse access(@PathVariable Long projectId, Authentication authentication) {
        ProjectMember member = members.getProjectMember(projectId, authentication.getName());
        boolean canWrite = member.getRole() == ProjectRole.OWNER || member.getRole() == ProjectRole.EDITOR;
        return new CollaborationAccessResponse(authentication.getName(), canWrite);
    }

    public record CollaborationAccessResponse(String username, boolean canWrite) {}
}
