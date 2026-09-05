package com.codebreak.backend.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceFolderMetadataRepository
        extends JpaRepository<WorkspaceFolderMetadata, Long> {

    List<WorkspaceFolderMetadata>
    findByProjectIdOrderByPathAsc(
            Long projectId
    );

    Optional<WorkspaceFolderMetadata>
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