package com.codebreak.backend.project;

import com.codebreak.backend.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.security.SecureRandom;
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

    private static final String JOIN_CODE_CHARACTERS =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom JOIN_CODE_RANDOM =
            new SecureRandom();

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

        StringBuilder code =
                new StringBuilder();

        for (int i = 0; i < 8; i++) {

            code.append(
                    JOIN_CODE_CHARACTERS.charAt(
                            JOIN_CODE_RANDOM.nextInt(
                                    JOIN_CODE_CHARACTERS.length()
                            )
                    )
            );
        }

        return code.toString();
    }
}