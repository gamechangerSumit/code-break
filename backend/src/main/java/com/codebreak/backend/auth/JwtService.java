package com.codebreak.backend.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;


    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                secret.getBytes(
                        StandardCharsets.UTF_8
                )
        );

    }


    public String generateToken(
            String username
    ) {

        Date now =
                new Date();


        /*
         * Development token validity:
         * 24 hours
         */

        Date expiration =
                new Date(
                        now.getTime()
                                +
                                1000L
                                        *
                                        60
                                        *
                                        60
                                        *
                                        24
                );


        return Jwts.builder()

                .subject(
                        username
                )

                .issuedAt(
                        now
                )

                .expiration(
                        expiration
                )

                .signWith(
                        getSigningKey()
                )

                .compact();

    }


    public String extractUsername(
            String token
    ) {

        return Jwts.parser()

                .verifyWith(
                        getSigningKey()
                )

                .build()

                .parseSignedClaims(
                        token
                )

                .getPayload()

                .getSubject();

    }

}