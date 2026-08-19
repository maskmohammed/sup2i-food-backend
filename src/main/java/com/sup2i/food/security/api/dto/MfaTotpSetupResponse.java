package com.sup2i.food.security.api.dto;

import java.util.UUID;

public record MfaTotpSetupResponse(
    UUID methodId,
    String secret,
    String otpauthUri
) {
}