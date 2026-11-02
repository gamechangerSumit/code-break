package com.codebreak.backend.activity;

import com.codebreak.backend.project.ProjectMemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectActivityService {
    private final ProjectActivityRepository repository;
    private final ProjectMemberService members;

    @Transactional
    public void record(Long projectId, String actor, String type, String message) {
        repository.save(ProjectActivity.builder().projectId(projectId).actor(actor)
                .type(type).message(message).build());
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> list(Long projectId, String username) {
        members.requireReadAccess(projectId, username);
        return repository.findTop100ByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(e -> new ActivityResponse(e.getId(), e.getActor(), e.getType(),
                        e.getMessage(), e.getCreatedAt())).toList();
    }

    public record ActivityResponse(Long id, String actor, String type, String message,
                                   java.time.LocalDateTime createdAt) {}
}
