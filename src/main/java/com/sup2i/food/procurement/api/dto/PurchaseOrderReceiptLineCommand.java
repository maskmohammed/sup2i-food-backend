package com.sup2i.food.procurement.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseOrderReceiptLineCommand(
    UUID lineId,
    UUID purchaseOrderItemId,
    BigDecimal quantity,
    BigDecimal unitCost,
    String lotNumber,
    OffsetDateTime expiresAt
) {
}