package com.codebreak.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MinioService {

    private static final int MAX_INITIALIZATION_ATTEMPTS = 30;
    private static final long RETRY_DELAY_MILLIS = 2_000L;

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    public void initializeBucket() {
        Exception lastFailure = null;

        for (int attempt = 1; attempt <= MAX_INITIALIZATION_ATTEMPTS; attempt++) {
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder()
                                .bucket(bucket)
                                .build()
                );

                if (!exists) {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder()
                                    .bucket(bucket)
                                    .build()
                    );
                }

                System.out.println("[OBJECT STORAGE] Bucket ready: " + bucket);
                return;
            } catch (Exception exception) {
                lastFailure = exception;

                if (attempt == MAX_INITIALIZATION_ATTEMPTS) {
                    break;
                }

                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(
                            "Interrupted while waiting for object storage bucket: " + bucket,
                            interrupted
                    );
                }
            }
        }

        throw new IllegalStateException(
                "Unable to initialize object storage bucket after "
                        + MAX_INITIALIZATION_ATTEMPTS
                        + " attempts: "
                        + bucket,
                lastFailure
        );
    }
}
