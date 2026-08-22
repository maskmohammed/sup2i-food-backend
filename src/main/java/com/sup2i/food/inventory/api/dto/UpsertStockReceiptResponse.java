package com.sup2i.food.inventory.api.dto;

public record UpsertStockReceiptResponse(
    StockReceiptResponse receipt,
    boolean replayed
) {
}