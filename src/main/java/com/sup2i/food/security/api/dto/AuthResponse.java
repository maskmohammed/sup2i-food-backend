package com.sup2i.food.security.api.dto;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserSummaryResponse user
) {
}