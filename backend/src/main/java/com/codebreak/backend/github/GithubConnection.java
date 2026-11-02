package com.codebreak.backend.github;

import com.codebreak.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "github_connections", uniqueConstraints =
        @UniqueConstraint(name = "uk_github_connection_user", columnNames = "user_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GithubConnection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(name = "github_username", nullable = false, length = 100)
    private String githubUsername;
    @Column(name = "encrypted_token", nullable = false, length = 4096)
    private String encryptedToken;
    @Column(name = "token_iv", nullable = false, length = 64)
    private String tokenIv;
    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;
    @PrePersist void onCreate() { if (connectedAt == null) connectedAt = LocalDateTime.now(); }
}
