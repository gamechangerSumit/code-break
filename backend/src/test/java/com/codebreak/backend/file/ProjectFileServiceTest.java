package com.codebreak.backend.file;

import com.codebreak.backend.project.ProjectMemberService;
import com.codebreak.backend.project.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProjectFileServiceTest {

    @Mock
    private ProjectFileRepository fileRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberService projectMemberService;

    @InjectMocks
    private ProjectFileService fileService;

    @Test
    void createFileReportsInvalidInputAsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> fileService.createFile(
                        42L,
                        new ProjectFileRequest(" ", "src/Main.java", ""),
                        "alice"
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(projectMemberService).requireWriteAccess(42L, "alice");
        verifyNoInteractions(projectRepository, fileRepository);
    }

    @Test
    void createFileRejectsNullRequestAsBadRequest() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> fileService.createFile(42L, null, "alice")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(projectMemberService).requireWriteAccess(42L, "alice");
        verifyNoInteractions(projectRepository, fileRepository);
    }
}
