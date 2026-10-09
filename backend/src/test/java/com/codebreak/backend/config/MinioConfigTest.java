package com.codebreak.backend.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioConfigTest {

    @Test
    void createsConfiguredBucketWhenItDoesNotExist() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        MinioConfig config = new MinioConfig();
        ReflectionTestUtils.setField(config, "bucket", "workspace-test");

        config.ensureMinioBucket(minioClient).run(new DefaultApplicationArguments(new String[0]));

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void doesNotCreateConfiguredBucketWhenItAlreadyExists() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        MinioConfig config = new MinioConfig();
        ReflectionTestUtils.setField(config, "bucket", "workspace-test");

        config.ensureMinioBucket(minioClient).run(new DefaultApplicationArguments(new String[0]));

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }
}
