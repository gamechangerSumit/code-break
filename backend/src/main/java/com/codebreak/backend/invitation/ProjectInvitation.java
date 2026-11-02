package com.codebreak.backend.invitation;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_invitations", uniqueConstraints =
        @UniqueConstraint(name = "uk_project_invite_token", columnNames = "token"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, unique = true, length = 64)
    private String token;
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
