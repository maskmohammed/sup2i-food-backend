package com.sup2i.food.waste.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.waste.domain.WasteCategory;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WasteRecordResponse(
    UUID id,
    UUID stockItemId,
    UUID stockLocationId,
    BigDecimal quantity,
    MeasurementUnit unit,
    UUID wasteReasonId,
    String wasteReasonCode,
    WasteCategory category,
    BigDecimal estimatedCost,
    String notes,
    UUID productId,
    UUID orderItemId,
    UUID productionRunItemId,
    UUID inventoryMovementId,
    UUID recordedBy,
    OffsetDateTime recordedAt,
    boolean replayed
) {
}