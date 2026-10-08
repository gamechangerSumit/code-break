package com.codebreak.backend.auth;

import com.codebreak.backend.user.User;
import com.codebreak.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("alice")
                .email("alice@example.com")
                .password("encoded-password")
                .build();
    }

    @Test
    void registerNormalizesUsernameAndEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("very-secure-password"))
                .thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserResponse response = authService.register(
                new RegisterRequest(
                        " alice ",
                        " ALICE@EXAMPLE.COM ",
                        "very-secure-password"
                )
        );

        assertEquals(1L, response.id());
        assertEquals("alice", response.username());
        assertEquals("alice@example.com", response.email());

        verify(passwordEncoder).encode("very-secure-password");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(
                        new RegisterRequest(
                                " alice ",
                                "alice@example.com",
                                "very-secure-password"
                        )
                )
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.register(
                        new RegisterRequest(
                                "alice",
                                " ALICE@EXAMPLE.COM ",
                                "very-secure-password"
                        )
                )
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginReturnsJwtForValidCredentials() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(
                "very-secure-password",
                "encoded-password"
        )).thenReturn(true);
        when(jwtService.generateToken("alice"))
                .thenReturn("jwt-token");

        AuthResponse response = authService.login(
                new LoginRequest(
                        " alice ",
                        "very-secure-password"
                )
        );

        assertEquals("jwt-token", response.token());
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(
                        new LoginRequest(
                                "alice",
                                "wrong-password"
                        )
                )
        );

        assertEquals(401, exception.getStatusCode().value());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginRejectsWrongPassword() {
        when(userRepository.findByUsername("alice"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.matches(
                "wrong-password",
                "encoded-password"
        )).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(
                        new LoginRequest(
                                "alice",
                                "wrong-password"
                        )
                )
        );

        assertEquals(401, exception.getStatusCode().value());
        verify(jwtService, never()).generateToken(any());
    }
}
