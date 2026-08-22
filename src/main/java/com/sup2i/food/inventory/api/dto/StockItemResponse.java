package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record StockItemResponse(
    UUID id,
    String subjectType,
    UUID productId,
    UUID variantId,
    UUID ingredientId,
    String subjectName,
    MeasurementUnit baseUnit,
    BigDecimal lowStockThreshold,
    boolean trackExpiry
) {
}