package com.sup2i.food.inventory.api.contract;

import com.sup2i.food.inventory.domain.InventoryMovementType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryMovementResponse(

    UUID id,

    InventoryMovementType movementType,

    BigDecimal quantity,

    String reason,

    OffsetDateTime createdAt
) {
}