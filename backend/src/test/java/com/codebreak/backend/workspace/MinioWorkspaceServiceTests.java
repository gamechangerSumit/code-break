package com.codebreak.backend.workspace;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class MinioWorkspaceServiceTests {

    @Test
    void buildsObjectKeysFromCanonicalWorkspacePaths() {
        MinioClient minioClient = mock(MinioClient.class);
        MinioWorkspaceService service = new MinioWorkspaceService(minioClient);

        assertEquals(
                "projects/12/files/src/main/App.java",
                service.buildObjectKey(12L, "/src\\main\\App.java/")
        );
    }

    @Test
    void rejectsTraversalPathsBeforeMinioAccess() {
        MinioClient minioClient = mock(MinioClient.class);
        MinioWorkspaceService service = new MinioWorkspaceService(minioClient);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildObjectKey(12L, "src/../../outside.txt")
        );

        verifyNoInteractions(minioClient);
    }
}
