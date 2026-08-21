package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.common.domain.MeasurementUnit;

import java.util.List;
import java.util.UUID;

public record IngredientResponse(
    UUID id,
    String code,
    String name,
    MeasurementUnit baseUnit,
    boolean active,
    List<DietaryReferenceResponse> allergens
) {
}