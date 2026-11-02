package com.codebreak.backend.history;

import jakarta.validation.constraints.Size;

public record CreateSnapshotRequest(
        @Size(max = 240) String message
) {}
