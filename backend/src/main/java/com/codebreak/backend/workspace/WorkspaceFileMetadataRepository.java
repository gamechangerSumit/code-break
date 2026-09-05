package com.codebreak.backend.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceFileMetadataRepository
        extends JpaRepository<WorkspaceFileMetadata, Long> {

    List<WorkspaceFileMetadata>
    findByProjectIdOrderByPathAsc(
            Long projectId
    );

    Optional<WorkspaceFileMetadata>
    findByProjectIdAndPath(
            Long projectId,
            String path
    );

    boolean existsByProjectIdAndPath(
            Long projectId,
            String path
    );

    void deleteByProjectId(
            Long projectId
    );
}