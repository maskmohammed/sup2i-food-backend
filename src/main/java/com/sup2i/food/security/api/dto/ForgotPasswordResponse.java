package com.sup2i.food.security.api.dto;

public record ForgotPasswordResponse(
    String message,
    String devResetToken
) {
}
