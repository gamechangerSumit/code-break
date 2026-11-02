package com.codebreak.backend.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectActivityRepository extends JpaRepository<ProjectActivity, Long> {
    List<ProjectActivity> findTop100ByProjectIdOrderByCreatedAtDesc(Long projectId);
}
