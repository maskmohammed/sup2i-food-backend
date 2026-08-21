package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeItemResponse(
    UUID id,
    UUID ingredientId,
    String ingredientCode,
    String ingredientName,
    BigDecimal quantity,
    MeasurementUnit unit,
    BigDecimal wasteFactor,
    boolean critical
) {
}