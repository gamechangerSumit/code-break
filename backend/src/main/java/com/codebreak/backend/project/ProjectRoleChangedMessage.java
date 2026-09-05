package com.codebreak.backend.project;

public record ProjectRoleChangedMessage(

        Long projectId,

        Long memberId,

        Long userId,

        String username,

        ProjectRole role

) {
}