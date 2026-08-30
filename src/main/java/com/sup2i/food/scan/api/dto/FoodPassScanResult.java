package com.sup2i.food.scan.api.dto;

public record FoodPassScanResult(
    String type,
    FoodPassResponse foodPass,
    MealEligibility currentMealEligibility
) implements ScanResponse {

    public FoodPassScanResult(
        FoodPassResponse foodPass,
        MealEligibility currentMealEligibility
    ) {
        this(
            "FOOD_PASS",
            foodPass,
            currentMealEligibility
        );
    }
}