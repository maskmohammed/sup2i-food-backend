package com.sup2i.food.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "sup2i.security")
public record SecurityProperties(
    String localProviderCode,
    Jwt jwt,
    Duration refreshTokenTtl,
    LoginProtection loginProtection
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
}