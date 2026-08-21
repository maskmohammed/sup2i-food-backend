package com.sup2i.food.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record ProductDietaryMetadataResponse(
    UUID productId,
    List<ProductAllergenResponse> allergens,
    List<ProductDietaryTagResponse> dietaryTags
) {
}