package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateMenuItemRequest(

    @NotNull
    UUID productId,

    UUID variantId,

    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal priceDelta,

    Boolean defaultItem,

    Boolean active,

    @Min(0)
    int displayOrder
) {
}