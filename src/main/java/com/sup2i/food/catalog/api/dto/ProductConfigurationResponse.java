package com.sup2i.food.catalog.api.dto;

import java.util.List;
import java.util.UUID;

public record ProductConfigurationResponse(
    UUID productId,
    List<ProductVariantResponse> variants,
    List<ProductOptionGroupResponse> optionGroups
) {
}