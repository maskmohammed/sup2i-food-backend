package com.sup2i.food.security.service;

import java.time.Instant;
import java.util.UUID;

public record AuthenticationTokens(
    UUID userId,
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken
) {
}