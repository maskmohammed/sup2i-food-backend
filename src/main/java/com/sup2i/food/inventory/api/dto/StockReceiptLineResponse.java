package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockReceiptLineResponse(
    UUID id,
    UUID stockItemId,
    BigDecimal quantity,
    MeasurementUnit unit,
    BigDecimal unitCost,
    String lotNumber,
    OffsetDateTime expiresAt,
    UUID generatedLotId,
    UUID inventoryMovementId
) {
}