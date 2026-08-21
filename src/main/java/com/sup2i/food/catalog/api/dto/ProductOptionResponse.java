package com.sup2i.food.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductOptionResponse(
    UUID id,
    String name,
    BigDecimal priceDelta,
    boolean active,
    int displayOrder
) {
}