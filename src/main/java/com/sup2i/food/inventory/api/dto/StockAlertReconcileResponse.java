package com.sup2i.food.inventory.api.dto;

public record StockAlertReconcileResponse(
    int created,
    int resolved,
    int retained,
    int active,
    int expiryWarningDays
) {
}