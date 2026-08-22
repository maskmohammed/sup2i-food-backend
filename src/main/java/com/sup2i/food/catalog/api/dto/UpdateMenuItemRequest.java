package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateMenuItemRequest(

    @NotNull
    UUID productId,

    UUID variantId,

    @NotNull
    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    @NotNull
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal priceDelta,

    @NotNull
    Boolean defaultItem,

    @NotNull
    Boolean active,

    @Min(0)
    int displayOrder
) {
}