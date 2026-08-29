package com.sup2i.food.scan.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ScanTokenHasher {

    public String hash(
        String rawValue
    ) {

        try {

            MessageDigest digest =
                MessageDigest.getInstance(
                    "SHA-256"
                );

            byte[] bytes =
                digest.digest(
                    rawValue.getBytes(
                        StandardCharsets.UTF_8
                    )
                );

            return HexFormat
                .of()
                .formatHex(
                    bytes
                );

        } catch (
            NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                "SHA-256 is unavailable.",
                exception
            );
        }
    }
}