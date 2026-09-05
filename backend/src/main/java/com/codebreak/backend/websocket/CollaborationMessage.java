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

    private String content;

    private List<WorkspaceFilePayload> files;

    private List<String> folders;


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