package com.sup2i.food.security.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenHashService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        String rawToken = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);

        return new GeneratedToken(
            rawToken,
            hash(rawToken)
        );
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

            byte[] hashed = digest.digest(
                rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }

    public record GeneratedToken(
        String rawToken,
        String tokenHash
    ) {
    }
}