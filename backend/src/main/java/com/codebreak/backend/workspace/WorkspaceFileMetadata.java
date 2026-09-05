package com.codebreak.backend.workspace;

import com.codebreak.backend.project.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "workspace_files",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_workspace_file_project_path",
                        columnNames = {"project_id", "path"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_workspace_file_project",
                        columnList = "project_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceFileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false
    )
    private Project project;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, length = 1000)
    private String objectKey;

    @Column(nullable = false)
    private Long size;

    @Column(nullable = false)
    private Long version;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {

        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;
        updatedAt = now;

        if (version == null) {
            version = 1L;
        }

        if (size == null) {
            size = 0L;
        }
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}