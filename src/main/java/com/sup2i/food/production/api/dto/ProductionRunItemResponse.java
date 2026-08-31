package com.sup2i.food.production.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ProductionRunItemResponse(
    UUID id,
    UUID productId,
    UUID variantId,
    UUID recipeId,
    BigDecimal targetQuantity,
    BigDecimal preparedQuantity,
    MeasurementUnit unit,
    BigDecimal estimatedUnitCost,
    OffsetDateTime preparationStartedAt,
    OffsetDateTime preparationCompletedAt,
    String notes
) {
}