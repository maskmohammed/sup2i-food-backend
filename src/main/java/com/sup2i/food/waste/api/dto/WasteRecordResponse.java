package com.sup2i.food.waste.api.dto;

import com.sup2i.food.waste.domain.WasteRecord;
import com.sup2i.food.waste.domain.WasteType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WasteRecordResponse(
    UUID id,
    UUID campusId,
    UUID stockLocationId,
    UUID recipeId,
    UUID ingredientId,
    UUID productId,
    UUID orderItemId,
    WasteType wasteType,
    BigDecimal quantity,
    String unit,
    BigDecimal estimatedCost,
    String reasonText,
    String photoUrl,
    UUID recordedBy,
    OffsetDateTime recordedAt,
    UUID inventoryMovementId
) {

    public static WasteRecordResponse from(
        WasteRecord record
    ) {
        return new WasteRecordResponse(
            record.getId(),
            record.getCampus() == null
                ? null
                : record.getCampus()
                    .getId(),
            record.getStockLocation() == null
                ? null
                : record.getStockLocation()
                    .getId(),
            record.getRecipe() == null
                ? null
                : record.getRecipe()
                    .getId(),
            record.getIngredient() == null
                ? null
                : record.getIngredient()
                    .getId(),
            record.getProduct() == null
                ? null
                : record.getProduct()
                    .getId(),
            record.getOrderItem() == null
                ? null
                : record.getOrderItem()
                    .getId(),
            record.getWasteType(),
            record.getQuantity(),
            record.getUnit().name(),
            record.getEstimatedCost(),
            record.getReasonText(),
            record.getPhotoUrl(),
            record.getRecordedBy()
                .getId(),
            record.getRecordedAt(),
            record.getInventoryMovement() == null
                ? null
                : record.getInventoryMovement()
                    .getId()
        );
    }
}