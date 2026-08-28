package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.common.domain.MealType;

import java.time.LocalDate;
import java.util.UUID;

public record MealEntitlementResponse(
    UUID id,
    MealType mealType,
    LocalDate validFrom,
    LocalDate validTo,
    Integer totalQuota,
    int dailyLimit
) {
}
