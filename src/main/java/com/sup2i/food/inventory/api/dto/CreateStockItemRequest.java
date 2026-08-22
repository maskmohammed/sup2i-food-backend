package com.sup2i.food.inventory.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateStockItemRequest(

    UUID productId,

    UUID variantId,

    UUID ingredientId,

    @NotNull
    MeasurementUnit baseUnit,

    @DecimalMin("0.000")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal lowStockThreshold,

    Boolean trackExpiry
) {

    @AssertTrue(
        message =
            "exactly one stock subject must be provided"
    )
    public boolean isSubjectValid() {

        int subjects = 0;

        if (productId != null) {
            subjects++;
        }

        if (variantId != null) {
            subjects++;
        }

        if (ingredientId != null) {
            subjects++;
        }

        return subjects == 1;
    }
}