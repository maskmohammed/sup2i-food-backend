package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.subscription.domain.FoodPassStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FoodPassResponse(
    UUID id,
    UUID studentId,
    UUID credentialId,
    String cardNumber,
    FoodPassStatus status,
    OffsetDateTime issuedAt,
    OffsetDateTime expiresAt,
    String blockReason,
    UUID issuedById,
    String qrToken
) {
}