package com.codebreak.backend.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AuthController.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void registerRejectsShortPassword() throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "alice@example.com",
                                  "password": "short"
                                }
                                """)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation failed"))
        .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void registerRejectsInvalidEmail() throws Exception {
        mockMvc.perform(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "email": "not-an-email",
                                  "password": "very-secure-password"
                                }
                                """)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void loginRejectsMissingCredentials() throws Exception {
        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": ""
                                }
                                """)
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void malformedJsonReturnsConsistentBadRequestPayload() throws Exception {
        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json")
        )
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void unsupportedMethodReturnsConsistentErrorPayload() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.error").value("Method not allowed"));
    }

    @Test
    void unexpectedServiceFailureDoesNotExposeInternalDetails() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new IllegalStateException("private database detail"));

        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "very-secure-password"
                                }
                                """)
        )
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.error").value("Internal server error"))
        .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("private database detail")
        )));
    }

    @Test
    void validLoginReturnsToken() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("test-token"));

        mockMvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "alice",
                                  "password": "very-secure-password"
                                }
                                """)
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").value("test-token"));

        verify(authService).login(any(LoginRequest.class));
    }
}
