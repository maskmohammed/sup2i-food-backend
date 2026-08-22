package com.sup2i.food.inventory.api.dto;

public record StockAlertMutationResponse(
    StockAlertResponse alert,
    boolean replayed
) {
}