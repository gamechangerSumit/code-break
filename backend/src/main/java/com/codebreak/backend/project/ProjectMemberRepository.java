package com.codebreak.backend.project;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByProjectIdAndUserId(
            Long projectId,
            Long userId
    );

    List<ProjectMember> findByProjectId(
            Long projectId
    );

    List<ProjectMember> findByUserId(
            Long userId
    );

    boolean existsByProjectIdAndUserId(
            Long projectId,
            Long userId
    );

    void deleteByProjectId(
            Long projectId
    );
}