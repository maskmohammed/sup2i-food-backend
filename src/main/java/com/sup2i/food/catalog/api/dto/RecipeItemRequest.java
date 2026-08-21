package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RecipeItemRequest(

    @NotNull
    UUID ingredientId,

    @NotNull
    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    @NotNull
    MeasurementUnit unit,

    @DecimalMin("0.0000")
    @DecimalMax(
        value = "1.0000",
        inclusive = false
    )
    @Digits(
        integer = 1,
        fraction = 4
    )
    BigDecimal wasteFactor,

    Boolean critical
) {
}