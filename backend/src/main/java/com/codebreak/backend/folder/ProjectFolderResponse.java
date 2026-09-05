package com.codebreak.backend.folder;

public record ProjectFolderResponse(

        Long id,

        String name,

        String path,

        Long parentId

) {
}