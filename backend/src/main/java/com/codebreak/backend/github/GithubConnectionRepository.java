package com.codebreak.backend.github;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GithubConnectionRepository extends JpaRepository<GithubConnection, Long> {
    Optional<GithubConnection> findByUserId(Long userId);
    void deleteByUserId(Long userId);
}
