package com.codebreak.backend.workspace;

import com.codebreak.backend.project.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "workspace_folders",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_workspace_folder_project_path",
                        columnNames = {"project_id", "path"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_workspace_folder_project",
                        columnList = "project_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceFolderMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "project_id",
            nullable = false
    )
    private Project project;

    @Column(
            nullable = false,
            length = 1000
    )
    private String path;

    @Column(
            nullable = false,
            length = 500
    )
    private String name;

    @Column(
            name = "parent_path",
            length = 1000
    )
    private String parentPath;

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
    }

    @PreUpdate
    protected void onUpdate() {

        updatedAt =
                LocalDateTime.now();
    }
}