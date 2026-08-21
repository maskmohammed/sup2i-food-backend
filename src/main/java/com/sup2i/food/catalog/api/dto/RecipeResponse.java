package com.sup2i.food.catalog.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RecipeResponse(
    UUID id,
    UUID productId,
    UUID variantId,
    String variantName,
    int version,
    boolean active,
    OffsetDateTime effectiveFrom,
    OffsetDateTime effectiveTo,
    List<RecipeItemResponse> items
) {
}