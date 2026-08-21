package com.sup2i.food.catalog.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductOptionRequest(

    @NotBlank
    @Size(max = 120)
    String name,

    BigDecimal priceDelta,

    Boolean active,

    @Min(0)
    int displayOrder
) {
}