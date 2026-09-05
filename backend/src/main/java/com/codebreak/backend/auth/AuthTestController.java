package com.codebreak.backend.auth;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthTestController {

    @GetMapping("/api/test")
    public String test(Authentication authentication) {

        return "Hello " + authentication.getName()
                + ", your JWT is valid!";
    }
}