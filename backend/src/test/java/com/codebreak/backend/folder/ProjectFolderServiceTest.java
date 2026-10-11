package com.codebreak.backend.folder;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProjectFolderServiceTest {

    @Mock
    private ProjectFolderRepository folderRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberService projectMemberService;

    @InjectMocks
    private ProjectFolderService folderService;

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret",
            "src/../../secret",
            "..\\..\\secret",
            "src/./main"
    })
    void importRejectsTraversalSegmentsBeforeDatabaseLookup(String path) {
        assertThrows(
                ResponseStatusException.class,
                () -> folderService.importFolders(
                        42L,
                        List.of(path),
                        "alice"
                )
        );

        verify(projectMemberService).requireWriteAccess(42L, "alice");
        verifyNoInteractions(projectRepository, folderRepository);
    }

    @Test
    void importRejectsOversizedNormalizedPathBeforeDatabaseLookup() {
        String path = "a".repeat(1001);

        assertThrows(
                ResponseStatusException.class,
                () -> folderService.importFolders(
                        42L,
                        List.of(path),
                        "alice"
                )
        );

        verify(projectMemberService).requireWriteAccess(42L, "alice");
        verifyNoInteractions(projectRepository, folderRepository);
    }
}
