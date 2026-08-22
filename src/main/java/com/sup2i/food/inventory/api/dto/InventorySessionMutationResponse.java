package com.sup2i.food.inventory.api.dto;

public record InventorySessionMutationResponse(
    InventorySessionResponse session,
    boolean replayed
) {
}