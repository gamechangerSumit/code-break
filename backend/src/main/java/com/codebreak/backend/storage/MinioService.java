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

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @PostConstruct
    public void initializeBucket() {

        try {

            boolean exists =
                    minioClient.bucketExists(
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

                System.out.println(
                        "[MINIO] Bucket created: "
                                + bucket
                );

            } else {

                System.out.println(
                        "[MINIO] Bucket already exists: "
                                + bucket
                );
            }

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to initialize MinIO bucket: "
                            + bucket,
                    exception
            );
        }
    }
}