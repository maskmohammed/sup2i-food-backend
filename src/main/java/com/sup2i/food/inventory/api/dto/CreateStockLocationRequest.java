package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.inventory.domain.StockLocationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateStockLocationRequest(

    @NotNull
    UUID locationId,

    @NotBlank
    @Size(max = 120)
    String name,

    @NotNull
    StockLocationType type,

    Boolean active
) {
}