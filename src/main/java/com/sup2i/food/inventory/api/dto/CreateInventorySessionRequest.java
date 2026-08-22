package com.sup2i.food.inventory.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateInventorySessionRequest(

    @NotNull
    UUID stockLocationId,

    @Size(max = 5000)
    String notes
) {
}