package com.codebreak.backend.workspace;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinioWorkspaceServiceTest {

    @Test
    void buildObjectKeyNormalizesSeparators() {
        MinioWorkspaceService service =
                new MinioWorkspaceService((MinioClient) null);

        assertEquals(
                "projects/42/files/src/Main.java",
                service.buildObjectKey(42L, "\\src\\Main.java")
        );
    }

    @Test
    void buildObjectKeyRejectsTraversal() {
        MinioWorkspaceService service =
                new MinioWorkspaceService((MinioClient) null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildObjectKey(42L, "../secret.txt")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildObjectKey(42L, "src/../../secret.txt")
        );
    }

    @Test
    void buildObjectKeyRejectsOversizedPath() {
        MinioWorkspaceService service =
                new MinioWorkspaceService((MinioClient) null);

        String path = "a".repeat(1001);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.buildObjectKey(42L, path)
        );
    }
}
