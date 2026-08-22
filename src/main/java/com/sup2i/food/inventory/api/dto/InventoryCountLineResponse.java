package com.sup2i.food.inventory.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryCountLineResponse(
    UUID id,
    UUID stockItemId,
    BigDecimal systemPhysicalQuantity,
    BigDecimal systemReservedQuantity,
    BigDecimal countedQuantity,
    BigDecimal differenceQuantity,
    UUID countedBy,
    OffsetDateTime countedAt,
    UUID adjustmentMovementId,
    String reason
) {
}