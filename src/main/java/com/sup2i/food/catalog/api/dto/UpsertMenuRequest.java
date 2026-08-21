package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.catalog.domain.MenuPricingMode;

public record UpsertMenuRequest(
    MenuPricingMode pricingMode,
    String description,
    Boolean active
) {
}