package com.sup2i.food.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;
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
    Cors cors,
    RateLimit rateLimit,
    PasswordPolicy passwordPolicy
) {

    /**
     * audience() resolve le claim "aud" des JWT :
     * renvoie la valeur explicite, sinon l'issuer par défaut.
     */
    public String audience() {
        if (jwt == null) {
            return null;
        }

        return (jwt.audience() == null
                || jwt.audience().isBlank())
            ? jwt.issuer()
            : jwt.audience();
    }

    public record Jwt(
        String issuer,
        Duration accessTokenTtl,
        String secretBase64,
        String audience
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

    public record RateLimit(
        boolean enabled,
        List<Bucket> buckets
    ) {

        public record Bucket(
            String name,
            int capacity,
            Duration refillPeriod,
            int refillTokens
        ) {
        }
    }

    public record PasswordPolicy(
        boolean enabled,
        Integer minLength,
        boolean requireUpper,
        boolean requireLower,
        boolean requireDigit,
        boolean requireSpecial,
        List<String> forbidden
    ) {
    }
}