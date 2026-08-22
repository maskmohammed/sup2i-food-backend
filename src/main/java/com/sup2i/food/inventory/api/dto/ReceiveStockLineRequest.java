package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReceiveStockLineRequest(

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
    MeasurementUnit unit,

    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal unitCost,

    @Size(max = 120)
    String lotNumber,

    OffsetDateTime expiresAt
) {
}