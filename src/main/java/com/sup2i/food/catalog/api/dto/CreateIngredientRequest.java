package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIngredientRequest(

    @NotBlank
    @Size(max = 80)
    String code,

    @NotBlank
    @Size(max = 150)
    String name,

    @NotNull
    MeasurementUnit baseUnit,

    Boolean active
) {
}