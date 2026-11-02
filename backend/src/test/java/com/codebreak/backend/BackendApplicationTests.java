package com.codebreak.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codebreak.backend.user.User;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BackendApplicationTests {

    @Autowired
    private ObjectMapper objectMapper;

    // The application creates its bucket during startup; keep this context test
    // isolated from external object storage infrastructure.
    @MockitoBean
    private MinioClient minioClient;

    @Test
    void contextLoads() {
        assertThat(minioClient).isNotNull();
    }

    @Test
    void userJsonNeverExposesPasswordHash() throws Exception {
        User user = User.builder()
                .id(1L)
                .username("test-user")
                .email("test@example.com")
                .password("hashed-password")
                .createdAt(LocalDateTime.now())
                .build();

        String json = objectMapper.writeValueAsString(user);

        assertThat(json).contains("test-user", "test@example.com");
        assertThat(json).doesNotContain("password", "hashed-password");
    }
}
