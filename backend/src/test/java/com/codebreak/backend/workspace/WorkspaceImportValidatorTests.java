package com.codebreak.backend.workspace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkspaceImportValidatorTests {

    @Test
    void acceptsBoundedImport() {
        assertDoesNotThrow(() -> WorkspaceImportValidator.validate(
                List.of(new WorkspaceService.WorkspaceImportFile("src/App.java", "App.java", "class App {}")),
                List.of("src")
        ));
    }

    @Test
    void validatesSingleFileContentForDirectSavesAndStorageReads() {
        assertDoesNotThrow(() -> WorkspaceImportValidator.validateFileContent("hello"));
        String oversized = "x".repeat(WorkspaceImportValidator.MAX_FILE_BYTES + 1);
        assertThrows(IllegalArgumentException.class,
                () -> WorkspaceImportValidator.validateFileContent(oversized));
    }

    @Test
    void rejectsTooManyFilesOrFolders() {
        var file = new WorkspaceService.WorkspaceImportFile("a.txt", "a.txt", "");
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(
                        java.util.Collections.nCopies(WorkspaceImportValidator.MAX_FILES + 1, file),
                        List.of()
                ));
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(
                        List.of(),
                        java.util.Collections.nCopies(WorkspaceImportValidator.MAX_FOLDERS + 1, "src")
                ));
    }

    @Test
    void rejectsNullEntriesAndInvalidPaths() {
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(java.util.Arrays.asList((WorkspaceService.WorkspaceImportFile) null), List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(
                        List.of(new WorkspaceService.WorkspaceImportFile("../secret", "secret", "")),
                        List.of()
                ));
    }

    @Test
    void rejectsOversizedIndividualFile() {
        String content = "x".repeat(WorkspaceImportValidator.MAX_FILE_BYTES + 1);
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(
                        List.of(new WorkspaceService.WorkspaceImportFile("large.txt", "large.txt", content)),
                        List.of()
                ));
    }

    @Test
    void rejectsOversizedAggregateContent() {
        String content = "x".repeat(WorkspaceImportValidator.MAX_FILE_BYTES);
        var files = java.util.stream.IntStream.range(0, 26)
                .mapToObj(index -> new WorkspaceService.WorkspaceImportFile(
                        "file-" + index + ".txt", "file-" + index + ".txt", content))
                .toList();
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(files, List.of()));
    }

    @Test
    void rejectsBlankFolderPath() {
        assertThrows(IllegalArgumentException.class, () ->
                WorkspaceImportValidator.validate(List.of(), List.of("  ")));
    }
}
