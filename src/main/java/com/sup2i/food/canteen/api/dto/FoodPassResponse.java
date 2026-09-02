package com.sup2i.food.canteen.api.dto;

import com.sup2i.food.security.api.dto.StudentSummaryResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FoodPassResponse(
    UUID id,
    String cardNumber,
    String status,
    OffsetDateTime expiresAt,
    StudentSummaryResponse student
) {
}