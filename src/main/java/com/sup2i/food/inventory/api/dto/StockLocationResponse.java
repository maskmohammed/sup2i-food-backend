package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.inventory.domain.StockLocationType;

import java.util.UUID;

public record StockLocationResponse(
    UUID id,
    UUID locationId,
    String name,
    StockLocationType type,
    boolean active
) {
}