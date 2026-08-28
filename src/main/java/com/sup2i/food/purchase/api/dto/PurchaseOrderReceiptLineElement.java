package com.sup2i.food.purchase.api.dto;

import com.sup2i.food.purchase.domain.PurchaseOrderReceiptLine;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PurchaseOrderReceiptLineElement(
    UUID id,
    UUID purchaseOrderLineId,
    BigDecimal quantity,
    String unit,
    BigDecimal unitCost,
    String lotNumber,
    OffsetDateTime expiresAt,
    UUID generatedLotId,
    UUID inventoryMovementId
) {

    public static PurchaseOrderReceiptLineElement from(
        PurchaseOrderReceiptLine line
    ) {
        return new PurchaseOrderReceiptLineElement(
            line.getId(),
            line.getPurchaseOrderLine()
                .getId(),
            line.getQuantity(),
            line.getUnit().name(),
            line.getUnitCost(),
            line.getLotNumber(),
            line.getExpiresAt(),
            line.getGeneratedLot() == null
                ? null
                : line.getGeneratedLot()
                    .getId(),
            line.getInventoryMovement() == null
                ? null
                : line.getInventoryMovement()
                    .getId()
        );
    }
}