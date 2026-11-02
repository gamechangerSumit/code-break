package com.codebreak.backend.workspace;

/**
 * Canonical validation for workspace-relative paths.
 *
 * Paths are stored with forward slashes and must never contain traversal
 * segments, empty interior segments, control characters, or oversized names.
 */
public final class WorkspacePath {

    private static final int MAX_PATH_LENGTH = 1024;
    private static final int MAX_SEGMENT_LENGTH = 255;

    private WorkspacePath() {
    }

    public static String normalize(String path) {
        if (path == null) {
            return "";
        }

        String normalized = path.trim().replace('\\\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("Workspace path is too long");
        }
        if (normalized.contains("//")) {
            throw new IllegalArgumentException("Invalid workspace path");
        }

        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid workspace path");
            }
            if (segment.length() > MAX_SEGMENT_LENGTH) {
                throw new IllegalArgumentException("Workspace path segment is too long");
            }
            for (int index = 0; index < segment.length(); index++) {
                if (Character.isISOControl(segment.charAt(index))) {
                    throw new IllegalArgumentException("Invalid workspace path");
                }
            }
        }

        return normalized;
    }
}
