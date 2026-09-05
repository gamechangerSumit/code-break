package com.codebreak.backend.file;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/files")
@RequiredArgsConstructor
public class ProjectFileController {

    private final ProjectFileService fileService;


    // =========================================
    // CREATE FILE
    // =========================================

    @PostMapping
    public ResponseEntity<ProjectFile> createFile(

            @PathVariable Long projectId,

            @RequestBody ProjectFileRequest request,

            Authentication authentication

    ) {

        ProjectFile file =
                fileService.createFile(

                        projectId,

                        request,

                        authentication.getName()

                );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(file);
    }


    // =========================================
    // GET FILES
    // =========================================

    @GetMapping
    public ResponseEntity<List<ProjectFile>> getFiles(

            @PathVariable Long projectId,

            Authentication authentication

    ) {

        return ResponseEntity.ok(

                fileService.getProjectFiles(

                        projectId,

                        authentication.getName()

                )

        );
    }


    // =========================================
    // IMPORT PROJECT
    // =========================================

    @PostMapping("/import")
    public ResponseEntity<List<ProjectFile>>
    importProject(

            @PathVariable Long projectId,

            @RequestBody ProjectImportRequest request,

            Authentication authentication

    ) {

        List<ProjectFile> files =
                fileService.importFiles(

                        projectId,

                        request,

                        authentication.getName()

                );


        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(files);
    }


    // =========================================
    // UPDATE FILE
    // =========================================

    @PutMapping("/{fileId}")
    public ResponseEntity<ProjectFile> updateFile(

            @PathVariable Long projectId,

            @PathVariable Long fileId,

            @RequestBody ProjectFileUpdateRequest request,

            Authentication authentication

    ) {

        ProjectFile file =
                fileService.updateFile(

                        projectId,

                        fileId,

                        request.content(),

                        authentication.getName()

                );


        return ResponseEntity.ok(file);
    }


    // =========================================
    // DELETE FILE
    // =========================================

    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(

            @PathVariable Long projectId,

            @PathVariable Long fileId,

            Authentication authentication

    ) {

        fileService.deleteFile(

                projectId,

                fileId,

                authentication.getName()

        );


        return ResponseEntity
                .noContent()
                .build();
    }
}