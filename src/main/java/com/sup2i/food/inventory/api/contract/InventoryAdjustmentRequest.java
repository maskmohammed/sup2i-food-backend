package com.sup2i.food.inventory.api.contract;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record InventoryAdjustmentRequest(

    @NotNull
    UUID stockItemId,

    @NotNull
    UUID stockLocationId,

    @NotNull
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantityDelta,

    @NotNull
    InventoryAdjustmentReason reason,

    @Size(max = 2000)
    String comment
) {

    @AssertTrue(
        message =
            "quantityDelta must be non-zero"
    )
    public boolean isQuantityDeltaValid() {

        return quantityDelta == null
            || quantityDelta.signum() != 0;
    }
}