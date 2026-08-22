package com.sup2i.food.inventory.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateStockItemRequest(

    @DecimalMin("0.000")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal lowStockThreshold,

    @NotNull
    Boolean trackExpiry
) {
}