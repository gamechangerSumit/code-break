package com.codebreak.backend.folder;

public record ProjectFolderRequest(

        String name,

        String path,

        Long parentId

) {
}