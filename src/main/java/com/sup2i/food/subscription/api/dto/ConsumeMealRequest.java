package com.sup2i.food.subscription.api.dto;

import com.sup2i.food.common.domain.MealType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record ConsumeMealRequest(

    @NotNull
    UUID studentId,

    @NotNull
    MealType mealType,

    LocalDate date
) {
}