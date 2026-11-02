package com.codebreak.backend.invitation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitation, Long> {
    Optional<ProjectInvitation> findByToken(String token);
}
