package com.codebreak.backend.history;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WorkspaceVersionRepository extends JpaRepository<WorkspaceVersion, Long> {
    List<WorkspaceVersion> findTop100ByProjectIdAndFilePathOrderByCreatedAtDesc(Long projectId, String filePath);
    Optional<WorkspaceVersion> findByIdAndProjectId(Long id, Long projectId);
    long countByProjectIdAndFilePath(Long projectId, String filePath);
}
