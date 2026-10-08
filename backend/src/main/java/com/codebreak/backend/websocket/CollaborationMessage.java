package com.codebreak.backend.websocket;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationMessage {

    private String type;

    private Long projectId;

    private String filePath;

    private String username;

    /**
     * Kept for backward compatibility.
     *
     * For CODE_CHANGE the new collaboration flow uses
     * `changes` instead of replacing the whole document.
     */
    private String content;

    /**
     * Monaco incremental changes.
     */
    private List<CodeChange> changes;

    private List<WorkspaceFilePayload> files;

    private List<String> folders;


    // =====================================================
    // CODE CHANGE
    // =====================================================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CodeChange {

        /**
         * Zero-based character offset in the document.
         */
        private Integer rangeOffset;

        /**
         * Number of characters replaced.
         */
        private Integer rangeLength;

        /**
         * Text inserted at the range.
         */
        private String text;
    }


    // =====================================================
    // COMPLETE WORKSPACE FILE
    // =====================================================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceFilePayload {

        private String path;

        private String content;
    }
}