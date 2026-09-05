package com.codebreak.backend.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectFileRepository
        extends JpaRepository<ProjectFile, Long> {

    List<ProjectFile> findByProjectId(Long projectId);

    Optional<ProjectFile> findByProjectIdAndPath(
            Long projectId,
            String path
    );
}