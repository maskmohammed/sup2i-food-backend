package com.sup2i.food.waste.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordWasteCommand(
    UUID stockItemId,
    UUID stockLocationId,
    BigDecimal quantity,
    MeasurementUnit unit,
    UUID wasteReasonId,
    BigDecimal estimatedCost,
    String notes,
    UUID productId,
    UUID orderItemId,
    UUID productionRunItemId
) {
}