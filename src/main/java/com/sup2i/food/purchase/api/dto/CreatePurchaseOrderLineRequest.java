package com.sup2i.food.purchase.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePurchaseOrderLineRequest(

    UUID productId,

    UUID variantId,

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

    @DecimalMin("0.00")
    @Digits(
        integer = 10,
        fraction = 2
    )
    BigDecimal unitPrice
) {

    @AssertTrue(
        message =
            "exactly one purchase order line subject must be provided"
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