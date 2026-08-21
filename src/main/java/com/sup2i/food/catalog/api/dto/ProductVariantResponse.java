package com.sup2i.food.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantResponse(
    UUID id,
    String name,
    String sku,
    BigDecimal priceDelta,
    boolean active,
    int displayOrder
) {
}