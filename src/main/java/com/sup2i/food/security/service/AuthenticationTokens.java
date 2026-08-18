package com.sup2i.food.security.service;

import java.time.Instant;

public record AuthenticationTokens(
    String accessToken,
    Instant accessTokenExpiresAt,
    String refreshToken
) {
}