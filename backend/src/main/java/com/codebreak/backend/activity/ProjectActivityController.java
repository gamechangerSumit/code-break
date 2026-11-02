package com.codebreak.backend.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/activity")
@RequiredArgsConstructor
public class ProjectActivityController {
    private final ProjectActivityService service;
    @GetMapping
    public List<ProjectActivityService.ActivityResponse> list(
            @PathVariable Long projectId, Authentication auth) {
        return service.list(projectId, auth.getName());
    }
}
