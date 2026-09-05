package com.codebreak.backend.folder;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/projects/{projectId}/folders"
)
@RequiredArgsConstructor
public class ProjectFolderController {

    private final ProjectFolderService folderService;


    // =========================================
    // CREATE FOLDER
    // =========================================

    @PostMapping
    public ResponseEntity<ProjectFolderResponse>
    createFolder(

            @PathVariable Long projectId,

            @RequestBody ProjectFolderRequest request,

            Authentication authentication

    ) {

        ProjectFolderResponse folder =
                folderService.createFolder(

                        projectId,

                        request,

                        authentication.getName()

                );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(folder);
    }


    // =========================================
    // GET FOLDERS
    // =========================================

    @GetMapping
    public ResponseEntity<
            List<ProjectFolderResponse>
            >
    getFolders(

            @PathVariable Long projectId,

            Authentication authentication

    ) {

        return ResponseEntity.ok(

                folderService.getProjectFolders(

                        projectId,

                        authentication.getName()

                )

        );
    }


    // =========================================
    // DELETE FOLDER
    // =========================================

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void>
    deleteFolder(

            @PathVariable Long projectId,

            @PathVariable Long folderId,

            Authentication authentication

    ) {

        folderService.deleteFolder(

                projectId,

                folderId,

                authentication.getName()

        );


        return ResponseEntity
                .noContent()
                .build();
    }
    @PostMapping("/import")
    public ResponseEntity<
            List<ProjectFolderResponse>
            >
    importFolders(

            @PathVariable Long projectId,

            @RequestBody List<String> paths,

            Authentication authentication

    ) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        folderService.importFolders(
                                projectId,
                                paths,
                                authentication.getName()
                        )
                );
    }
}