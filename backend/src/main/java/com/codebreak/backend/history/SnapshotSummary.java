package com.codebreak.backend.history;

import java.time.LocalDateTime;

public record SnapshotSummary(
        Long id,
        String filePath,
        String createdBy,
        String message,
        LocalDateTime createdAt,
        int contentLength
) {
    public static SnapshotSummary from(WorkspaceSnapshot snapshot) {
        return new SnapshotSummary(
                snapshot.getId(),
                snapshot.getFilePath(),
                snapshot.getCreatedBy(),
                snapshot.getMessage(),
                snapshot.getCreatedAt(),
                snapshot.getContent().length()
        );
    }
}
