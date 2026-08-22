package com.sup2i.food.inventory.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CountInventoryItemRequest(

    @NotNull
    @DecimalMin("0.000")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal countedQuantity,

    @Size(max = 5000)
    String reason
) {
}