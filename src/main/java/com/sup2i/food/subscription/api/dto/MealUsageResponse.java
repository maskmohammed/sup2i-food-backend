package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.common.domain.MealType;
import com.sup2i.food.subscription.domain.MealUsageStatus;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MealUsageResponse(
    UUID id,
    UUID studentId,
    UUID entitlementId,
    MealType mealType,
    LocalDate usageDate,
    OffsetDateTime consumedAt,
    UUID validatedById,
    MealUsageStatus status
) {
}