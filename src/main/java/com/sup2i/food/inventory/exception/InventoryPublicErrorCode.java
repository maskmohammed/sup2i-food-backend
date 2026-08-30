package com.sup2i.food.inventory.exception;

public enum InventoryPublicErrorCode {

    RESOURCE_NOT_FOUND,
    INVALID_STOCK_ADJUSTMENT,
    IDEMPOTENCY_CONFLICT,
    CONCURRENT_MODIFICATION,
    OUT_OF_STOCK
}