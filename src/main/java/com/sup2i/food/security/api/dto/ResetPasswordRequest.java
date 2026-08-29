package com.sup2i.food.security.api.dto;

import com.sup2i.food.security.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(

    @NotBlank
    String token,

    @NotBlank
    @StrongPassword
    String newPassword
) {
}