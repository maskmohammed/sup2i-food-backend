package com.sup2i.food.inventory.api.dto;

public record InventoryAdjustmentResponse(
    InventoryMovementResponse movement,
    StockBalanceResponse balance,
    boolean replayed
) {
}