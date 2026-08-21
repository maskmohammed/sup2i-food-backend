package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.catalog.domain.MenuPricingMode;

import java.util.List;
import java.util.UUID;

public record MenuResponse(
    UUID id,
    UUID productId,
    MenuPricingMode pricingMode,
    String description,
    boolean active,
    List<MenuSectionResponse> sections
) {
}