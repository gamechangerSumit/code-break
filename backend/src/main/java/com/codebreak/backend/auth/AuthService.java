package com.codebreak.backend.auth;

import com.codebreak.backend.user.User;
import com.codebreak.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserResponse register(RegisterRequest request) {
        if (passwordExceedsBcryptLimit(request.password())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes"
            );
        }

        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (username.length() < 3 || username.length() > 32) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Username must be between 3 and 32 characters after trimming"
            );
        }

        if (userRepository.existsByUsername(username)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Username already exists"
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Email already exists"
            );
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(request.password()))
                .build();

        User saved = userRepository.save(user);

        return new UserResponse(
                saved.getId(),
                saved.getUsername(),
                saved.getEmail()
        );
    }

    public AuthResponse login(LoginRequest request) {
        if (passwordExceedsBcryptLimit(request.password())) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Invalid username or password"
            );
        }

        String username = request.username().trim();

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                UNAUTHORIZED,
                                "Invalid username or password"
                        )
                );

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword()
        )) {
            throw new ResponseStatusException(
                    UNAUTHORIZED,
                    "Invalid username or password"
            );
        }

        return new AuthResponse(
                jwtService.generateToken(user.getUsername())
        );
    }
    private static boolean passwordExceedsBcryptLimit(String password) {
        return password != null
                && password.getBytes(StandardCharsets.UTF_8).length
                > MAX_BCRYPT_PASSWORD_BYTES;
    }
}
