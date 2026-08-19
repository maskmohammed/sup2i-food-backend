package com.sup2i.food.security.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(

    @NotBlank
    String refreshToken
) {
}