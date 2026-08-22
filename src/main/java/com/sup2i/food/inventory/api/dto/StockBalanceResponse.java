package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record StockBalanceResponse(
    UUID stockItemId,
    UUID stockLocationId,
    BigDecimal physicalQuantity,
    BigDecimal reservedQuantity,
    BigDecimal availableQuantity,
    MeasurementUnit unit
) {
}