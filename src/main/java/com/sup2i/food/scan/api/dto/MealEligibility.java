package com.sup2i.food.scan.api.dto;

public record MealEligibility(
    boolean eligible,
    String mealType,
    Integer remainingQuota,
    String denialCode
) {
}