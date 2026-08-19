package com.sup2i.food.security.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record MfaTotpConfirmRequest(

    @NotBlank
    @Email
    String email,

    @NotBlank
    @Size(min = 8)
    String password,

    @NotNull
    UUID methodId,

    @NotBlank
    @Pattern(regexp = "\\d{6}")
    String code
) {
}