package com.sup2i.food.production.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductionRunItemCommand(
    UUID productId,
    UUID variantId,
    UUID recipeId,
    BigDecimal targetQuantity,
    MeasurementUnit unit,
    BigDecimal estimatedUnitCost,
    String notes
) {
}