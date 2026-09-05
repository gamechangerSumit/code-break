package com.codebreak.backend.websocket;

import com.codebreak.backend.project.ProjectRole;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleChangedMessage {

    private Long projectId;

    private Long memberId;

    private Long userId;

    private String username;

    private ProjectRole role;
}