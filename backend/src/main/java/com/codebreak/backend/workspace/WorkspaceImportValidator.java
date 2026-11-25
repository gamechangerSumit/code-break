package com.codebreak.backend.workspace;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Bounds workspace imports before they trigger object-storage and database work.
 */
public final class WorkspaceImportValidator {

    public static final int MAX_FILES = 5_000;
    public static final int MAX_FOLDERS = 5_000;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final long MAX_TOTAL_FILE_BYTES = 50L * 1024 * 1024;
    private static final int MAX_NAME_LENGTH = 255;

    private WorkspaceImportValidator() {
    }

    public static int validateFileContent(String content) {
        int bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("Workspace file exceeds the 2 MiB limit");
        }
        return bytes;
    }

    public static void validate(
            List<WorkspaceService.WorkspaceImportFile> files,
            List<String> folders
    ) {
        if (files != null && files.size() > MAX_FILES) {
            throw new IllegalArgumentException("Workspace import contains too many files");
        }
        if (folders != null && folders.size() > MAX_FOLDERS) {
            throw new IllegalArgumentException("Workspace import contains too many folders");
        }

        long totalBytes = 0;
        if (files != null) {
            for (WorkspaceService.WorkspaceImportFile file : files) {
                if (file == null) {
                    throw new IllegalArgumentException("Workspace import contains an empty file entry");
                }
                String path = WorkspacePath.normalize(file.path());
                if (path.isBlank()) {
                    throw new IllegalArgumentException("Workspace import file path is required");
                }
                if (file.name() != null && file.name().length() > MAX_NAME_LENGTH) {
                    throw new IllegalArgumentException("Workspace import file name is too long");
                }

                int fileBytes = validateFileContent(file.content());
                totalBytes += fileBytes;
                if (totalBytes > MAX_TOTAL_FILE_BYTES) {
                    throw new IllegalArgumentException("Workspace import exceeds the 50 MiB total content limit");
                }
            }
        }

        if (folders != null) {
            for (String folder : folders) {
                if (WorkspacePath.normalize(folder).isBlank()) {
                    throw new IllegalArgumentException("Workspace import folder path is required");
                }
            }
        }
    }
}
