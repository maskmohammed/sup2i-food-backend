package com.sup2i.food.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record MenuItemResponse(
    UUID id,
    UUID productId,
    String productName,
    UUID variantId,
    String variantName,
    BigDecimal quantity,
    BigDecimal priceDelta,
    boolean defaultItem,
    boolean active,
    int displayOrder
) {
}