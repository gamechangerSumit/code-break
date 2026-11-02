package com.codebreak.backend.history;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkspaceSnapshotRepository extends JpaRepository<WorkspaceSnapshot, Long> {
    List<WorkspaceSnapshot> findTop100ByProjectIdAndFilePathOrderByCreatedAtDesc(Long projectId, String filePath);
    Optional<WorkspaceSnapshot> findByIdAndProjectId(Long id, Long projectId);
}
