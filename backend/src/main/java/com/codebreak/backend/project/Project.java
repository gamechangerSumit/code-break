package com.codebreak.backend.project;

import com.codebreak.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "projects",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_project_join_code",
                        columnNames = "join_code"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(
            name = "join_code",
            nullable = false,
            unique = true,
            length = 8
    )
    private String joinCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {

        createdAt = LocalDateTime.now();

        if (joinCode == null) {
            joinCode = generateJoinCode();
        }
    }

    private String generateJoinCode() {

        String characters =
                "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        StringBuilder code =
                new StringBuilder();

        for (int i = 0; i < 8; i++) {

            int index =
                    (int) (
                            Math.random()
                                    * characters.length()
                    );

            code.append(
                    characters.charAt(index)
            );
        }

        return code.toString();
    }
}