package com.sup2i.food.waste.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;
import com.sup2i.food.security.validation.SafeText;
import com.sup2i.food.waste.domain.WasteType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateWasteRecordRequest(

    UUID recipeId,

    UUID ingredientId,

    UUID productId,

    UUID orderItemId,

    UUID campusId,

    UUID stockLocationId,

    @NotNull
    WasteType wasteType,

    @NotNull
    @DecimalMin("0.001")
    @Digits(
        integer = 11,
        fraction = 3
    )
    BigDecimal quantity,

    @NotNull
    MeasurementUnit unit,

    @SafeText
    @Size(max = 500)
    String reasonText,

    @Size(max = 500)
    String photoUrl
) {

    @AssertTrue(
        message =
            "exactly one of recipe, ingredient or order item target must be provided"
    )
    public boolean isTargetValid() {

        int targets = 0;

        if (recipeId != null) {
            targets++;
        }

        if (ingredientId != null) {
            targets++;
        }

        if (orderItemId != null) {
            targets++;
        }

        return targets == 1;
    }
}