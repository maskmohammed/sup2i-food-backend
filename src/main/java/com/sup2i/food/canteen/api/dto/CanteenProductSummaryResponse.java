package com.sup2i.food.canteen.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CanteenProductSummaryResponse(
    UUID id,
    UUID categoryId,
    String sku,
    String barcode,
    String name,
    String imageUrl,
    BigDecimal basePrice,
    String currency,
    boolean available,
    boolean active
) {
}
