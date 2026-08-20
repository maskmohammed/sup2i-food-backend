package com.sup2i.food.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(
    prefix = "sup2i.security"
)
public record SecurityProperties(
    String localProviderCode,
    Jwt jwt,
    Duration refreshTokenTtl,
    LoginProtection loginProtection,
    Mfa mfa,
    Cors cors
) {

    public record Jwt(
        String issuer,
        Duration accessTokenTtl,
        String secretBase64
    ) {
    }

    public record LoginProtection(
        boolean enabled,
        int maxFailedAttempts,
        Duration failureWindow
    ) {
    }

    public record Mfa(
        boolean enabled,
        Set<String> requiredRoles,
        String encryptionKeyBase64,
        int recoveryCodeCount
    ) {
    }

    public record Cors(
        boolean enabled,
        Set<String> allowedOrigins,
        Duration maxAge
    ) {
    }
}