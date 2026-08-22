package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateStockTransferLineRequest(

    @NotNull
    UUID lineId,

    @NotNull
    UUID stockItemId,

    @NotNull
    @DecimalMin(
        value = "0.000",
        inclusive = false
    )
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    @NotNull
    MeasurementUnit unit
) {
}