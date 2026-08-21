package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductOptionComponentResponse(
    UUID id,
    UUID optionId,
    UUID componentProductId,
    String componentProductName,
    UUID componentVariantId,
    String componentVariantName,
    UUID ingredientId,
    String ingredientName,
    BigDecimal quantity,
    MeasurementUnit unit
) {
}