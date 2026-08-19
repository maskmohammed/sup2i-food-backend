package com.sup2i.food.security.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Locale;

@Service
public class TotpService {

    public static final int PERIOD_SECONDS =
        30;

    public static final int DIGITS =
        6;

    private static final int WINDOW =
        1;

    public boolean verify(
        byte[] secret,
        String submittedCode,
        OffsetDateTime lastUsedAt
    ) {
        if (
            submittedCode == null
            || !submittedCode.matches(
                "\\d{6}"
            )
        ) {
            return false;
        }

        Instant now =
            Instant.now();

        long currentCounter =
            counter(now);

        long lastUsedCounter =
            lastUsedAt == null
                ? Long.MIN_VALUE
                : counter(
                    lastUsedAt.toInstant()
                );

        for (
            int offset = -WINDOW;
            offset <= WINDOW;
            offset++
        ) {
            long candidateCounter =
                currentCounter + offset;

            if (
                candidateCounter
                    <= lastUsedCounter
            ) {
                continue;
            }

            String expected =
                generate(
                    secret,
                    candidateCounter
                );

            if (
                MessageDigest.isEqual(
                    expected.getBytes(
                        StandardCharsets.US_ASCII
                    ),
                    submittedCode.getBytes(
                        StandardCharsets.US_ASCII
                    )
                )
            ) {
                return true;
            }
        }

        return false;
    }

    public String currentCode(
        byte[] secret
    ) {
        return generate(
            secret,
            counter(
                Instant.now()
            )
        );
    }

    public String codeFor(
        byte[] secret,
        Instant instant
    ) {
        return generate(
            secret,
            counter(instant)
        );
    }

    private long counter(
        Instant instant
    ) {
        return instant
            .getEpochSecond()
            / PERIOD_SECONDS;
    }

    private String generate(
        byte[] secret,
        long counter
    ) {
        try {
            byte[] counterBytes =
                ByteBuffer
                    .allocate(Long.BYTES)
                    .putLong(counter)
                    .array();

            Mac mac =
                Mac.getInstance(
                    "HmacSHA1"
                );

            mac.init(
                new SecretKeySpec(
                    secret,
                    "HmacSHA1"
                )
            );

            byte[] hash =
                mac.doFinal(
                    counterBytes
                );

            int offset =
                hash[
                    hash.length - 1
                ]
                    & 0x0f;

            int binary =
                (
                    (
                        hash[offset]
                            & 0x7f
                    )
                        << 24
                )
                    | (
                        (
                            hash[offset + 1]
                                & 0xff
                        )
                            << 16
                    )
                    | (
                        (
                            hash[offset + 2]
                                & 0xff
                        )
                            << 8
                    )
                    | (
                        hash[offset + 3]
                            & 0xff
                    );

            int otp =
                binary % 1_000_000;

            return String.format(
                Locale.ROOT,
                "%06d",
                otp
            );

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "Unable to calculate TOTP.",
                exception
            );
        }
    }
}