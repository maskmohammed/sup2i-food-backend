package com.sup2i.food.inventory.api.dto;

public record StockTransferMutationResponse(
    StockTransferResponse transfer,
    boolean replayed
) {
}