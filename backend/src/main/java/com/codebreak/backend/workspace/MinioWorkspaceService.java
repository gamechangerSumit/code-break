package com.codebreak.backend.workspace;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class MinioWorkspaceService {

    private static final int MAX_FILE_CONTENT_BYTES = 5 * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;


    // =====================================================
    // SAVE TEXT FILE
    // =====================================================

    public void saveFile(
            Long projectId,
            String path,
            String content
    ) {

        if (projectId == null ||
                path == null ||
                path.isBlank()) {
            throw new IllegalArgumentException(
                    "Project ID and file path are required"
            );
        }

        String objectKey =
                buildObjectKey(
                        projectId,
                        path
                );

        byte[] bytes =
                (content == null
                        ? ""
                        : content
                ).getBytes(StandardCharsets.UTF_8);

        try {

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(
                                    new ByteArrayInputStream(bytes),
                                    bytes.length,
                                    -1
                            )
                            .contentType(
                                    "text/plain; charset=utf-8"
                            )
                            .build()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to save file to MinIO: "
                            + path,
                    exception
            );
        }
    }


    // =====================================================
    // READ TEXT FILE
    // =====================================================

    public String readFile(
            Long projectId,
            String path
    ) {

        if (projectId == null ||
                path == null ||
                path.isBlank()) {
            throw new IllegalArgumentException(
                    "Project ID and file path are required"
            );
        }

        String objectKey =
                buildObjectKey(
                        projectId,
                        path
                );

        try (
                InputStream inputStream =
                        minioClient.getObject(
                                GetObjectArgs.builder()
                                        .bucket(bucket)
                                        .object(objectKey)
                                        .build()
                        )
        ) {

            byte[] bytes = inputStream.readNBytes(MAX_FILE_CONTENT_BYTES + 1);
            if (bytes.length > MAX_FILE_CONTENT_BYTES) {
                throw new IllegalStateException(
                        "Workspace file exceeds the maximum supported size: " + path
                );
            }

            return new String(bytes, StandardCharsets.UTF_8);

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to read file from MinIO: "
                            + path,
                    exception
            );
        }
    }


    // =====================================================
    // DELETE FILE
    // =====================================================

    public void deleteFile(
            Long projectId,
            String path
    ) {

        if (projectId == null ||
                path == null ||
                path.isBlank()) {
            return;
        }

        String objectKey =
                buildObjectKey(
                        projectId,
                        path
                );

        try {

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Failed to delete file from MinIO: "
                            + path,
                    exception
            );
        }
    }


    // =====================================================
    // OBJECT KEY
    // =====================================================

    public String buildObjectKey(
            Long projectId,
            String path
    ) {

        String normalizedPath =
                normalizePath(path);

        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException(
                    "File path cannot be empty"
            );
        }

        return "projects/"
                + projectId
                + "/files/"
                + normalizedPath;
    }


    // =====================================================
    // NORMALIZE PATH
    // =====================================================

    private String normalizePath(
            String path
    ) {

        if (path == null) {
            return "";
        }

        String normalized = path
                .replace("\\", "/")
                .replaceAll("/+", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "");

        if (normalized.length() > 1000 || normalized.indexOf(Character.MIN_VALUE) >= 0) {
            throw new IllegalArgumentException("File path is invalid");
        }

        for (String segment : normalized.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("File path contains an invalid segment");
            }
        }

        return normalized;
    }
}