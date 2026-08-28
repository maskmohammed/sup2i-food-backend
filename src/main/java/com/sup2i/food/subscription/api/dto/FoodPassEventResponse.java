package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.subscription.domain.FoodPassEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record FoodPassEventResponse(
    UUID id,
    FoodPassEventType eventType,
    String reason,
    UUID performedById,
    OffsetDateTime createdAt
) {
}