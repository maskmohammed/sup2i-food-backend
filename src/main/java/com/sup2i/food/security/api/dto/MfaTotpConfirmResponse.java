package com.sup2i.food.security.api.dto;

import java.util.List;

public record MfaTotpConfirmResponse(
    AuthResponse auth,
    List<String> recoveryCodes
) {
}