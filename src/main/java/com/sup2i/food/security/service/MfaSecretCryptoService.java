package com.sup2i.food.security.service;

import com.sup2i.food.security.config.SecurityProperties;
import com.sup2i.food.security.domain.MfaMethodType;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class MfaSecretCryptoService {

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom =
        new SecureRandom();

    public MfaSecretCryptoService(
        SecurityProperties properties
    ) {
        SecurityProperties.Mfa config =
            properties.mfa();

        if (
            config == null
            || config.encryptionKeyBase64() == null
            || config.encryptionKeyBase64().isBlank()
        ) {
            throw new IllegalStateException(
                "MFA encryption key is required."
            );
        }

        byte[] rawKey;

        try {
            rawKey =
                Base64.getDecoder().decode(
                    config.encryptionKeyBase64()
                );
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                "MFA encryption key must be valid Base64.",
                exception
            );
        }

        if (rawKey.length != 32) {
            throw new IllegalStateException(
                "MFA encryption key must contain exactly 32 bytes."
            );
        }

        this.key =
            new SecretKeySpec(
                rawKey,
                "AES"
            );
    }

    public byte[] encrypt(
        byte[] plaintext,
        UUID userId,
        MfaMethodType methodType
    ) {
        try {
            byte[] iv =
                new byte[IV_LENGTH];

            secureRandom.nextBytes(iv);

            Cipher cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                );

            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(
                    TAG_LENGTH_BITS,
                    iv
                )
            );

            cipher.updateAAD(
                aad(userId, methodType)
            );

            byte[] ciphertext =
                cipher.doFinal(plaintext);

            return ByteBuffer
                .allocate(
                    iv.length
                        + ciphertext.length
                )
                .put(iv)
                .put(ciphertext)
                .array();

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "Unable to encrypt MFA secret.",
                exception
            );
        }
    }

    public byte[] decrypt(
        byte[] encrypted,
        UUID userId,
        MfaMethodType methodType
    ) {
        if (
            encrypted == null
            || encrypted.length
                <= IV_LENGTH
        ) {
            throw new IllegalStateException(
                "Invalid encrypted MFA secret."
            );
        }

        try {
            ByteBuffer buffer =
                ByteBuffer.wrap(encrypted);

            byte[] iv =
                new byte[IV_LENGTH];

            buffer.get(iv);

            byte[] ciphertext =
                new byte[
                    buffer.remaining()
                ];

            buffer.get(ciphertext);

            Cipher cipher =
                Cipher.getInstance(
                    "AES/GCM/NoPadding"
                );

            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(
                    TAG_LENGTH_BITS,
                    iv
                )
            );

            cipher.updateAAD(
                aad(userId, methodType)
            );

            return cipher.doFinal(
                ciphertext
            );

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                "Unable to decrypt MFA secret.",
                exception
            );
        }
    }

    private byte[] aad(
        UUID userId,
        MfaMethodType methodType
    ) {
        String value =
            "sup2i-food:mfa:v1:"
                + userId
                + ":"
                + methodType.name();

        return value.getBytes(
            StandardCharsets.UTF_8
        );
    }
}