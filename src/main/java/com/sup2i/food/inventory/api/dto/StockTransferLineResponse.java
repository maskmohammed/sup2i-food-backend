package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record StockTransferLineResponse(
    UUID id,
    UUID stockItemId,
    BigDecimal quantity,
    MeasurementUnit unit,
    UUID transferOutMovementId,
    UUID transferInMovementId
) {
}