package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductOptionComponentRequest(

    UUID componentProductId,

    UUID componentVariantId,

    UUID ingredientId,

    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    MeasurementUnit unit
) {

    @AssertTrue(
        message =
            "exactly one component subject must be selected"
    )
    public boolean isExactlyOneSubjectSelected() {

        int count = 0;

        if (componentProductId != null) {
            count++;
        }

        if (componentVariantId != null) {
            count++;
        }

        if (ingredientId != null) {
            count++;
        }

        return count == 1;
    }
}