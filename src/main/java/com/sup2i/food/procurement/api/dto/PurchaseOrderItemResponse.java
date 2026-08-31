package com.sup2i.food.procurement.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderItemResponse(
    UUID id,
    UUID stockItemId,
    BigDecimal quantity,
    MeasurementUnit unit,
    BigDecimal unitPrice,
    BigDecimal receivedQuantity
) {
}