package com.codebreak.backend.auth;

import com.codebreak.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private UserRepository userRepository;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtService = mock(JwtService.class);
        userRepository = mock(UserRepository.class);
        filter = new JwtAuthenticationFilter(jwtService, userRepository);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesTokenWhenUserStillExists() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("alice");
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(
                "alice",
                SecurityContextHolder.getContext().getAuthentication().getPrincipal()
        );
        verify(userRepository).existsByUsername("alice");
    }

    @Test
    void doesNotAuthenticateTokenWhenUserWasDeleted() throws Exception {
        request.addHeader("Authorization", "Bearer valid-token");
        when(jwtService.extractUsername("valid-token")).thenReturn("deleted-user");
        when(userRepository.existsByUsername("deleted-user")).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository).existsByUsername("deleted-user");
    }

    @Test
    void malformedTokenDoesNotAuthenticateRequest() throws Exception {
        request.addHeader("Authorization", "Bearer malformed-token");
        when(jwtService.extractUsername("malformed-token"))
                .thenThrow(new IllegalArgumentException("invalid token"));

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    void requestWithoutBearerTokenContinuesAnonymously() throws Exception {
        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(jwtService, userRepository);
    }
}
