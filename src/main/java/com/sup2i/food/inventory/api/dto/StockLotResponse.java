package com.sup2i.food.inventory.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockLotResponse(
    UUID id,
    UUID stockItemId,
    UUID stockLocationId,
    String lotNumber,
    UUID supplierId,
    String supplierName,
    OffsetDateTime receivedAt,
    OffsetDateTime expiresAt,
    BigDecimal quantityReceived,
    BigDecimal quantityRemaining,
    BigDecimal unitCost
) {
}