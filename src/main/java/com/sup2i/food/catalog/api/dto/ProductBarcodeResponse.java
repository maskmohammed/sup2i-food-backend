package com.sup2i.food.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductBarcodeResponse(
    UUID id,
    UUID variantId,
    String barcode,
    BigDecimal packQuantity,
    boolean primary,
    boolean active
) {
}