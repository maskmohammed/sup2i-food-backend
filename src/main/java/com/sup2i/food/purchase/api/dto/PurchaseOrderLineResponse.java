package com.sup2i.food.purchase.api.dto;

import com.sup2i.food.purchase.domain.PurchaseOrderLine;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseOrderLineResponse(
    UUID id,
    UUID productId,
    UUID variantId,
    UUID ingredientId,
    BigDecimal quantity,
    BigDecimal receivedQuantity,
    BigDecimal remainingQuantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {

    public static PurchaseOrderLineResponse from(
        PurchaseOrderLine line
    ) {
        return new PurchaseOrderLineResponse(
            line.getId(),
            line.getProduct() == null
                ? null
                : line.getProduct()
                    .getId(),
            line.getVariant() == null
                ? null
                : line.getVariant()
                    .getId(),
            line.getIngredient() == null
                ? null
                : line.getIngredient()
                    .getId(),
            line.getQuantity(),
            line.getReceivedQuantity(),
            line.remainingQuantity(),
            line.getUnit().name(),
            line.getUnitPrice(),
            line.getLineTotal()
        );
    }
}