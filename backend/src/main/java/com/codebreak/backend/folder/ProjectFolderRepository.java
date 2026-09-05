package com.codebreak.backend.folder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFolderRepository
        extends JpaRepository<ProjectFolder, Long> {

    List<ProjectFolder> findByProjectId(
            Long projectId
    );

    Optional<ProjectFolder>
    findByProjectIdAndPath(
            Long projectId,
            String path
    );

    boolean existsByProjectIdAndPath(
            Long projectId,
            String path
    );

    List<ProjectFolder>
    findByParentId(
            Long parentId
    );
}