package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record ApplyInventoryAdjustmentRequest(

    @NotNull
    UUID stockItemId,

    @NotNull
    UUID stockLocationId,

    @NotNull
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal physicalDelta,

    @NotNull
    MeasurementUnit unit,

    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal unitCost,

    @NotNull
    UUID idempotencyKey,

    @NotBlank
    @Size(max = 150)
    String reason,

    @Size(max = 2000)
    String comment
) {

    @AssertTrue(
        message =
            "physicalDelta must be non-zero"
    )
    public boolean isDeltaValid() {

        return physicalDelta == null
            || physicalDelta.signum() != 0;
    }
}