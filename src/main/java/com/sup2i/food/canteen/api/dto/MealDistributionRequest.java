package com.sup2i.food.canteen.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record MealDistributionRequest(

    @NotBlank
    String foodPassToken,

    @NotBlank
    String mealType,

    UUID menuId,

    UUID terminalId

) {
}
