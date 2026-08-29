package com.sup2i.food.scan.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductScanProductResponse(
    UUID id,
    UUID categoryId,
    String sku,
    String barcode,
    String name,
    String imageUrl,
    BigDecimal basePrice,
    String currency,
    boolean available,
    boolean active,
    UUID variantId,
    String variantName,
    BigDecimal packQuantity
) {
}