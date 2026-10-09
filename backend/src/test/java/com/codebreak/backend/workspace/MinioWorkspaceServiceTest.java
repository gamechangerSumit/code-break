package com.codebreak.backend.workspace;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinioWorkspaceServiceTest {

    private static final int MAX_FILE_CONTENT_BYTES = 5 * 1024 * 1024;

    @Test
    void readsFileWithinSizeLimit() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.readNBytes(MAX_FILE_CONTENT_BYTES + 1))
                .thenReturn("hello".getBytes(StandardCharsets.UTF_8));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        MinioWorkspaceService service = new MinioWorkspaceService(minioClient);
        ReflectionTestUtils.setField(service, "bucket", "workspace-test");

        assertEquals("hello", service.readFile(1L, "src/Main.java"));
    }

    @Test
    void rejectsFileExceedingSizeLimit() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(response.readNBytes(MAX_FILE_CONTENT_BYTES + 1))
                .thenReturn(new byte[MAX_FILE_CONTENT_BYTES + 1]);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        MinioWorkspaceService service = new MinioWorkspaceService(minioClient);
        ReflectionTestUtils.setField(service, "bucket", "workspace-test");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.readFile(1L, "src/Large.java")
        );

        assertEquals(
                "Failed to read file from MinIO: src/Large.java",
                exception.getMessage()
        );
        assertEquals(
                "Workspace file exceeds the maximum supported size: src/Large.java",
                exception.getCause().getMessage()
        );
    }

    @Test
    void rejectsOversizedFileBeforeWritingToMinio() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioWorkspaceService service = new MinioWorkspaceService(minioClient);
        ReflectionTestUtils.setField(service, "bucket", "workspace-test");

        String oversizedContent = "x".repeat(MAX_FILE_CONTENT_BYTES + 1);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveFile(1L, "src/Large.java", oversizedContent)
        );

        assertEquals("Workspace file content exceeds the 5 MiB limit", exception.getMessage());
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

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
