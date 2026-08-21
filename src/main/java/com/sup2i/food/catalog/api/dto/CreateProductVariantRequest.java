package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductVariantRequest(

    @NotBlank
    @Size(max = 120)
    String name,

    @Size(max = 80)
    String sku,

    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal priceDelta,

    Boolean active,

    @Min(0)
    int displayOrder
) {
}