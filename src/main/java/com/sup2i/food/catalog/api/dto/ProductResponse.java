package com.sup2i.food.catalog.api.dto;

import com.sup2i.food.catalog.domain.ProductType;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    UUID categoryId,
    String categoryName,
    String sku,
    String name,
    String description,
    String imageUrl,
    ProductType productType,
    BigDecimal basePrice,
    BigDecimal taxRate,
    Integer preparationMinutes,
    boolean trackStock,
    boolean prepared,
    boolean active
) {
}