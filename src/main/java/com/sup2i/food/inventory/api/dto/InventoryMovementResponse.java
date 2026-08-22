package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.inventory.domain.InventoryMovementType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryMovementResponse(
    UUID id,
    UUID stockItemId,
    UUID stockLocationId,
    InventoryMovementType movementType,
    BigDecimal physicalDelta,
    BigDecimal reservedDelta,
    MeasurementUnit unit,
    BigDecimal unitCost,
    String referenceType,
    UUID referenceId,
    String reason,
    String comment,
    UUID performedBy,
    OffsetDateTime createdAt
) {
}