package com.sup2i.food.inventory.api.contract;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryItemResponse(

    UUID stockItemId,

    UUID stockLocationId,

    String itemType,

    String name,

    MeasurementUnit unit,

    BigDecimal physicalQuantity,

    BigDecimal reservedQuantity,

    BigDecimal availableQuantity,

    BigDecimal lowStockThreshold,

    boolean lowStock
) {
}