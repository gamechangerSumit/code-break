package com.codebreak.backend.activity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "project_activity", indexes = @Index(name = "idx_project_activity_recent", columnList = "project_id,created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProjectActivity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false)
    private Long projectId;
    @Column(nullable = false, length = 100)
    private String actor;
    @Column(nullable = false, length = 60)
    private String type;
    @Column(nullable = false, length = 1000)
    private String message;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
