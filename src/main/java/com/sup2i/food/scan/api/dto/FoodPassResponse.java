package com.sup2i.food.scan.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FoodPassResponse(
    UUID id,
    String cardNumber,
    String status,
    OffsetDateTime expiresAt,
    StudentSummary student
) {
}