package com.sup2i.food.order.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
    UUID id,
    UUID productId,
    UUID variantId,
    String productName,
    String variantName,
    String sku,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal discountAmount,
    BigDecimal lineTotal,
    BigDecimal taxRate,
    BigDecimal lineTax,
    String specialInstructions
) {
}